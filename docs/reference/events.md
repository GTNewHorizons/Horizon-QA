---
title: Test event log
description: Event format, full catalog, differ behavior, and programmatic access from inside a test.
tags:
  - reference
  - events
  - ci
---

# Test event log

Every test appends an ordered log of typed events. The same log is written to JUnit `<system-out>` for each `<testcase>` and to the server console (last 20 lines on failure). The goal is plain: **a CI failure must be diagnosable from the XML alone, without starting the client**.

Set `-Dhorizonqa.events=off` on the server JVM to disable recording. Emit sites use `Supplier`; when recording is off those suppliers are not called and payload work is skipped. See [JVM & system properties](jvm-flags.md).

## Format

Each line is `[t=NNN] [category] summary`. The tick is **simulated machine ticks** since test start, not wall-clock server ticks. A 200-tick recipe shows `t=200` even though the warp completed in milliseconds.

```text
[FAIL] mymod.assembler.basic — Expected 64× copper plate but found 32×
       [t=0]   [lifecycle] Test started at TestPos{x=256, y=5, z=256}
       [t=0]   [structure] MTEAssemblyLine formed (OBSERVED_ON_FIRST_POLL, 2ib/1ob/1eh)
       [t=0]   [resource]  Inserted 64× Aluminium Plate
       [t=0]   [energy]    EU supply: 480 EU/t × 1 A for 250t
       [t=2]   [recipe]    Started (480 EU/t × 200t)
       [t=202] [recipe]    Finished (took 200t)
       [t=202] [failure]   Expected 64× copper plate but found 32×
```

Reading the trace: inputs, EU supply, and recipe completion all look correct; only the output count is wrong. That pattern almost always indicates a recipe-definition problem rather than bad test setup.

## Where events come from

Three sources, in roughly the order they appear in a typical trace:

1. **Test API methods.** `fillHatch`, `Bus.insert`, `supplyEU`, `assertMachineFormed`, and similar helpers emit on success, at the call site.
2. **The warp differ.** During `Multiblock.runRecipe` or `fastForwardTicks(ticks, watched)`, the framework snapshots each watched controller after every simulated tick and diffs against the previous snapshot. `mProgresstime` going from 0 to positive becomes `RecipeStarted`; the controller TE vanishing becomes `MachineExploded`; and so on.
3. **Test lifecycle.** `TestStarted`, `TestFinished`, `AssertionFailed`, and `IsolationViolation` come from the runner itself.

!!! note "The differ only watches controllers you register"

    `fastForwardTicks(1000)` with no watched list emits no recipe events for that warp. `Multiblock.runRecipe` registers its controller automatically; the imperative helpers expect you to pass a list explicitly.

## Catalog

Records live in `com.gtnewhorizons.horizonqa.api.event`. All are Java records, all carry a `tick` and a one-line `summary()`.

### Lifecycle

`TestStarted`, `TestFinished`, `StructurePlaced`, `WarpStarted`, `WarpFinished`.

`WarpFinished.stopReason` is one of:

`completed`
:   The warp ran for its full duration without a stop predicate intervening.

`predicate`
:   The warp's stop condition fired — typical for `runRecipe` ending when the machine returns to idle.

`timeout`
:   The warp hit its `maxTicks` cap without the predicate triggering.

### Structure

| Record               | When it fires                                                                |
|----------------------|------------------------------------------------------------------------------|
| `MachineFormed`      | `assertFormed` succeeds, or the differ sees `mMachine` flip to true          |
| `MachineDeformed`    | `mMachine` flipped to false while the controller TE is still present         |
| `MachineExploded`    | Controller TE is gone, or `assertMachineExploded` passes                     |
| `MachineDisabled`    | Active machine stops without finishing a recipe                              |
| `StructureCheckRan`  | A multiblock helper ran `checkStructure(...)`                                |

`MachineFormed.cause` distinguishes "template loaded already formed" from "assertion forced it" from "formed mid-warp":

`OBSERVED_ON_FIRST_POLL`
:   The controller already reported formed at the first poll. Usually the structure template loaded a pre-formed multi.

`FORCED_BY_ASSERTION`
:   `assertFormed` found `mMachine == false`, ran `checkStructure(forceReset=true)`, and the machine then formed. A `StructureCheckRan` event sits immediately before this one.

`FORMED_DURING_WARP`
:   The controller flipped to formed mid-warp. Rare; usually a delayed block update.

### Recipe

| Record                                       | When it fires                                                   |
|----------------------------------------------|-----------------------------------------------------------------|
| `RecipeStarted`                              | `mProgresstime` went from 0 to positive                         |
| `RecipeProgressed`                           | Progress crossed 25 / 50 / 75 %                                 |
| `RecipeFinished`                             | Progress reached max and then dropped                           |
| `RecipeAborted`                              | Progress dropped before reaching max                            |
| `RecipeNotFound`                             | `runUntilMachineIdle` exited and no recipe ever started         |
| `TestRecipeInjected` / `TestRecipeRemoved`   | `withTestRecipe` injection / end-of-test cleanup                |

`RecipeAborted.reason` is the raw `CheckRecipeResult` id from GT (`item_output_full`, `power_overflow`, `no_fuel_found`, …). That id is usually enough on its own to identify the abort cause.

### Resource

`BusInserted` (bus, itemDisplay, count), `HatchFilled` (hatch, fluidName, amountMb, accepted), `ProgrammedCircuitSet` (bus, config).

### Energy

`EUSupplyJobRegistered` (hatch, voltage, amperage, durationTicks) fires whenever you call `supplyEU` or `Hatch.supply`. `EUBufferOverflow` fires when a supply job tries to push more EU than the hatch can hold — you rarely want to see this in a test log. `HatchVoltageMismatch` is emitted alongside an explosion when the last supply job exceeded the hatch tier.

### Maintenance

`MaintenanceIssueAppeared` fires when the differ sees one of the six tool flags flip from OK to broken. `issueType` is the tool name: `WRENCH`, `SCREWDRIVER`, `SOFT_MALLET`, `HARD_HAMMER`, `SOLDERING_TOOL`, `CROWBAR`. `MaintenanceFixed` fires from `fixAllMaintenanceIssues` and `Multiblock.fixMaintenance`.

### Diagnostic

`PollutionEmitted` (originChunk, amount, cumulative), `CleanroomEfficiencyChanged` (controller, efficiencyTenThousandths in `0–10000`, i.e. 0.00–100.00 %). `EventOverflow` is emitted at most once per test when the 10 000-event cap is exceeded.

### Failure

`AssertionFailed` (message, throwableType, failPos) and `IsolationViolation` (culprit, landedAtAbs, detail). Both also show up in the standard `<failure>` and `<error>` JUnit elements; the event-log copy gives each failure a tick position relative to the rest of the run.

## Differ notes

The differ snapshots `mProgresstime` once per simulated tick. A handful of edge cases follow from that:

- A recipe that starts and completes within one tick may emit only `RecipeFinished` with no `RecipeStarted`, because progress never appears non-zero between snapshots. Check the recipe duration if that looks wrong.
- `RecipeFinished` vs. `RecipeAborted`: the last observed progress within one tick of max counts as finished; a drop earlier counts as aborted. Hitting max and then failing post-processing on the next tick yields `RecipeFinished` followed by an explosion or deformation, **not** `RecipeAborted`.
- Maintenance changes are bitmask-diffed. Multiple flags flipping in one tick produce multiple `MaintenanceIssueAppeared` events in fixed tool order (wrench, screwdriver, soft mallet, …).
- The event clock advances **inside** the warp loop. Events from `runRecipe` use simulated ticks (e.g. `t=50` for a recipe that starts 50 ticks into a 200-tick warp), not the outer `@GameTest` wall tick. This is intentional.

## Programmatic access

The log is reachable from inside a test, which is occasionally useful when an assertion is more naturally phrased over the event stream than over machine state:

```java
@GameTest
public static void exactlyOneRecipeFinished(GameTestHelper helper) {
    helper.gtnh().multiblock(at(1, 0, 0)).runRecipe();

    long finished = helper.getRecorder().snapshot().stream()
        .filter(RecipeFinished.class::isInstance)
        .count();

    helper.assertEquals(1L, finished);
    helper.succeed();
}
```

`snapshot()` returns an unmodifiable list in emit order. Pattern-match on the concrete record type to read typed payload fields.

## Cost

With `-Dhorizonqa.events=off`, emit sites are `Supplier` instances that are never invoked: no payload work and no allocations.

With recording enabled, each simulated tick during a warp performs one adapter read per watched controller — six `int` fields and a comparison. An event record is allocated only when the diff detects a change. Typical counts: about five events for a 200-tick recipe, about ten for a 20 000-tick fusion run.

The per-test cap is **10 000 events** (`EventOverflow` is emitted at most once if you exceed it). Reaching the cap almost always means the test is emitting from a tight loop; file an issue if you have a genuine reason to go higher.

## When GT fields change

All GT-internal reads go through `GT5UnofficialAdapter`. If GT5 renames `mProgresstime`, splits `MTEMultiBlockBase`, or reshapes `CheckRecipeResult`, exactly one file fails to compile. Event records do not import `gregtech.*` directly, so adapter churn does not cascade into the public API.
