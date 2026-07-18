---
title: Debugging failed tests
description: Triage a failure from the JUnit XML and event trace, then reproduce and fix it in-game.
---

# Debugging failed tests

Triage order matters: the report first, the game second. [Design principle 8](../contributing/principles.md) commits every failure to being diagnosable from the JUnit XML alone. Most of the time you never need to launch a client.

## 1. Read the report

Start with `TEST-horizonqa.xml`, or with the reported-batch console tail. Automatic and manually reported batches print each `[FAIL]` or `[ERROR]` line plus the last 20 event-log lines of the failing test.

For each failed `<testcase>`:

1. **The `<failure>` message** should say what was expected and what was observed. If it does not, improve the test output; see [step 4](#4-make-the-next-failure-cheaper).
2. **The `<system-out>` event trace** says what happened, in order, with tick stamps:

```text
[t=    0] [lifecycle  ] Test mymod:EbfTests.smelt started at TestPos{x=256, y=64, z=256}
[t=    0] [structure  ] MTEElectricBlastFurnace formed at TestPos{x=258, y=64, z=258} (OBSERVED_ON_FIRST_POLL, 2ib/1ob/1eh)
[t=    0] [resource   ] Inserted 1× Nickel Dust into TestPos{x=257, y=64, z=258}
[t=    0] [energy     ] EU supply job: 1920 EU/t × 1 A for 900t into TestPos{x=259, y=64, z=258}
[t=    0] [lifecycle  ] Time-warp started (maxTicks=900, watching 1 controller(s))
[t=  900] [lifecycle  ] Time-warp finished after 900 simulated tick(s) (timeout)
[t=  900] [failure    ] Assertion failed: Machine never started processing within 900 ticks
```

Read it like a flight recorder: the fixture formed, an input was inserted, and virtual supply was scheduled, but no `RecipeStarted` event appeared during the watched warp. Check the complete recipe inputs, circuit, maintenance flags, and whether the supplied duration covers the attempted run. The full emitted catalog is in [Test event log](../reference/events.md).

3. **`horizonqa-result.json`** adds the run-level view: exit code, per-test status, and `issues[]` for anything that went wrong outside a test body.

## 2. Match the failure signature

| What you see                                                        | Likely cause                                              | Next step                                                                  |
|---------------------------------------------------------------------|-----------------------------------------------------------|-----------------------------------------------------------------------------|
| `runRecipe` says the machine never started; no `RecipeStarted`     | Recipe did not become active: inputs, circuit, maintenance, or supply | Compare inserted resources with the recipe and verify maintenance plus supply duration |
| `RecipeAborted` with a reason id                                    | GT rejected the running recipe                            | The reason is the raw `CheckRecipeResult` id (`item_output_full`, `power_overflow`, …) |
| `MachineExploded`                                                   | The watched controller tile disappeared                   | Inspect preceding events and the machine's real safety logic; the event cause is currently `UNKNOWN` |
| `EUBufferOverflow`                                                  | A supply job pushed again after the buffer was already full | Reduce supply duration or rate if the extra pushes are not intentional     |
| No `MachineFormed`; `StructureCheckRan` present                     | Template incomplete, altered, or rotation-sensitive       | Re-export the template; try `rotation = 0` to isolate                       |
| Wrong output count, recipe trace otherwise clean                    | Recipe definition or assertion semantics                  | Inspect the recipe map and use `ItemMatcher.count(n)` for fluent bus quantity checks |
| `<error>` instead of `<failure>`                                    | Infrastructure: template load, cleanup, config, report path | Read `issues[]` in `horizonqa-result.json`; exit code is `2`              |
| `IsolationViolation`                                                | This test placed a GregTech tile in the protected outer margin | Use the reported position and add cleanup or keep mutations inside the cell |
| `<skipped>` on a test you expected to run                           | `required = false` test failed, or it was blocked by a setup issue | Check `tests[]` for `blockedByIssueId`; reconsider whether it should be optional |

## 3. Reproduce in-game

When the XML is not enough (usually for visual or spatial problems), reproduce interactively. Interactive is the default mode, so a plain `runServer` works:

```bash
./gradlew :examples:runServer
```

Then re-run exactly what failed:

```text
/horizonqa run mymod:AssemblerTests.processesOneRecipe
/horizonqa runfailed                                     # repeat failures now recorded in this interactive session
```

Failed cells **stay placed** on the grid:

- Each cell shows a color-coded beacon, a highlight box, and floating text with the test name (the ID suffix) and status.
- Block-level assertion failures place a **red ghost block** with a label at the offending position.
- `/horizonqa tp <testId>` jumps to a placed cell after `/horizonqa runall`; `/horizonqa pos` then prints both world and test-local coordinates. The copied `helper.absolute(...)` form is for direct world APIs; standard Horizon-QA helpers expect the local values.

Iterate without restarting:

1. Edit the test, recompile (hotswap or `gradlew classes`).
2. `/horizonqa tp <testId>` jumps to a specific placed cell; `/horizonqa runthis` re-runs the cell you are standing inside; `/horizonqa runthat` re-runs the cell in your line of sight (within 64 blocks).
3. `/horizonqa clearall` when the grid gets crowded.

Full command details: [Commands](../reference/commands.md).

## 4. Make the next failure cheaper

If you had to launch the game to understand the failure, the test's output was the second bug. Before closing out:

- Rewrite assertion messages to state expected vs. observed (`"Output bus should contain 64 copper plates after recipe"`, not `"wrong count"`).
- If a warp produced no recipe events, pass world-absolute controller positions to `fastForwardTicks(n, watched)`. `helper.absolute("controller")` converts a label for that overload. `runRecipe()` registers its controller automatically.
- For assertions naturally phrased over history ("exactly one recipe ran"), assert on the event stream via `helper.getRecorder().snapshot()`; see [Programmatic access](../reference/events.md#programmatic-access).

## Related pages

- [Test event log](../reference/events.md) for the full catalog and differ behavior.
- [CI and JUnit reports](ci.md) for report files, exit codes, and selectors.
- [Negative assertions](negative-tests.md) for failures of the kind "something happened that never should".
