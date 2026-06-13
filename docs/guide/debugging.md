---
title: Debugging failed tests
description: Triage a failure from the JUnit XML and event trace, then reproduce and fix it in-game.
tags:
  - guides
  - debugging
  - ci
---

# Debugging failed tests

Triage order matters: the report first, the game second. [Design principle 8](../contributing/principles.md) commits every failure to being diagnosable from the JUnit XML alone. Most of the time you never need to launch a client.

## 1. Read the report

Start with `TEST-horizonqa.xml`, or with the console tail: the server prints each `[FAIL]`/`[ERROR]` line plus the last 20 event-log lines of the failing test.

For each failed `<testcase>`:

1. **The `<failure>` message** says what was expected vs. observed. If it does not, that is a bug in the test; see [step 4](#4-make-the-next-failure-cheaper).
2. **The `<system-out>` event trace** says what happened, in order, with tick stamps:

```text
[t=0]   [lifecycle]   Test started at TestPos{x=256, y=5, z=256}
[t=0]   [structure]   MTEElectricBlastFurnace formed (OBSERVED_ON_FIRST_POLL, 2ib/1ob/1eh)
[t=0]   [resource]    Inserted 1× Nickel Dust into input bus #0
[t=0]   [energy]      EU supply job: 1920 EU/t × 1 A for 900t into energy hatch #0
[t=1]   [maintenance] Maintenance issue 'WRENCH' appeared at TestPos{x=257, y=5, z=257}
[t=900] [recipe]      No recipe ran at TestPos{x=257, y=5, z=257} (last check result: no_recipe)
[t=900] [failure]     Assertion failed: EBF should have produced 4× Nickel Aluminide ingots
```

Read it like a flight recorder: structure formed, inputs in, EU flowing, but a maintenance issue appeared and no recipe ever started. The fix is `fixMaintenance()`, and no client launch was needed. The full event catalog, including differ edge cases, is in [Test event log](../reference/events.md).

3. **`horizonqa-result.json`** adds the run-level view: exit code, per-test status, and `issues[]` for anything that went wrong outside a test body.

## 2. Match the failure signature

| What you see                                                        | Likely cause                                              | Next step                                                                  |
|---------------------------------------------------------------------|-----------------------------------------------------------|-----------------------------------------------------------------------------|
| Timeout, trace has no `RecipeStarted`                               | Recipe never matched: inputs, circuit, voltage tier, maintenance | Check `MaintenanceIssueAppeared`, `RecipeNotFound`, inserted items vs. recipe |
| `RecipeAborted` with a reason id                                    | GT rejected the running recipe                            | The reason is the raw `CheckRecipeResult` id (`item_output_full`, `power_overflow`, …) |
| `MachineExploded` preceded by `HatchVoltageMismatch`                | EU supply above the hatch tier                            | Lower the supplied voltage or use a higher-tier hatch in the template       |
| `EUBufferOverflow`                                                  | Supply job pushes more EU than the hatch buffer holds     | Reduce amperage or duration                                                 |
| No `MachineFormed`; `StructureCheckRan` present                     | Template incomplete, altered, or rotation-sensitive       | Re-export the template; try `rotation = 0` to isolate                       |
| Wrong output count, recipe trace otherwise clean                    | Recipe definition problem, not test setup                 | Inspect the recipe map; compare against `RecipeFinished` payload            |
| `<error>` instead of `<failure>`                                    | Infrastructure: template load, cleanup, config, report path | Read `issues[]` in `horizonqa-result.json`; exit code is `2`              |
| `IsolationViolation`                                                | A previous test leaked state into this cell               | Find the culprit test named in the event; add missing cleanup there         |
| `<skipped>` on a test you expected to run                           | `required = false` test failed, or it was blocked by a setup issue | Check `tests[]` for `blockedByIssueId`; reconsider whether it should be optional |

## 3. Reproduce in-game

When the XML is not enough (usually for visual or spatial problems), reproduce interactively. Interactive is the default mode, so a plain `runServer` works:

```bash
./gradlew :examples:runServer
```

Then re-run exactly what failed:

```text
/horizonqa run mymod:AssemblerTests.processesOneRecipe
/horizonqa runfailed                                     # everything that failed last batch
```

Failed cells **stay placed** on the grid:

- Each cell shows a color-coded beacon, a highlight box, and floating text with the test id and status.
- Block-level assertion failures place **red ghost blocks** with a label at the offending position; expected blocks render green.
- `/horizonqa tp <testId>` jumps to a placed cell after `/horizonqa runall`; `/horizonqa pos` then prints world and test-relative coordinates and suggests the matching `helper.absolute(x, y, z)` call.

Iterate without restarting:

1. Edit the test, recompile (hotswap or `gradlew classes`).
2. `/horizonqa tp <testId>` jumps to a specific placed cell; `/horizonqa runthis` re-runs the cell you are standing inside; `/horizonqa runthat` re-runs the cell in your line of sight (within 64 blocks).
3. `/horizonqa clearall` when the grid gets crowded.

Full command details: [Commands](../reference/commands.md).

## 4. Make the next failure cheaper

If you had to launch the game to understand the failure, the test's output was the second bug. Before closing out:

- Rewrite assertion messages to state expected vs. observed (`"Output bus should contain 64 copper plates after recipe"`, not `"wrong count"`).
- If a warp produced no recipe events, pass the controllers to watch: `fastForwardTicks(n, controllers)`. An unwatched warp emits nothing. `runRecipe()` registers its controller automatically.
- For assertions naturally phrased over history ("exactly one recipe ran"), assert on the event stream via `helper.getRecorder().snapshot()`; see [Programmatic access](../reference/events.md#programmatic-access).

## Related pages

- [Test event log](../reference/events.md) for the full catalog and differ behavior.
- [CI & JUnit reports](ci.md) for report files, exit codes, and selectors.
- [Negative assertions](negative-tests.md) for failures of the kind "something happened that never should".
