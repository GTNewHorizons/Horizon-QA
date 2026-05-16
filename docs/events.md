# Test Events

Tests record an ordered log of typed events as they run. The log lands in two places: the JUnit `<system-out>` of each `<testcase>`, and the console (the last 20 lines, only when a test fails). The point of it is that when CI breaks, you should be able to figure out why from the log alone, without rebooting the game.

Pass `-Dgametest.events=off` on the server JVM to make recording a no-op. Supplier lambdas at every emit site stop being invoked, so payload computation is also skipped.

## Format

Each line is `[t=NNN] [category] summary`. The tick is simulated machine ticks since test start, not wall-clock server ticks. A 200-tick recipe shows `t=200` even though the warp completed in milliseconds.

```
[FAIL] mymod.assembler.basic — Expected 64× copper plate but found 32×
       [t=0]   [lifecycle] Test started at TestPos{x=256, y=5, z=256}
       [t=0]   [structure] MTEAssemblyLine formed (OBSERVED_ON_FIRST_POLL, 2ib/1ob/1eh)
       [t=0]   [resource]  Inserted 64× Aluminium Plate
       [t=0]   [energy]    EU supply: 480 EU/t × 1 A for 250t
       [t=2]   [recipe]    Started (480 EU/t × 200t)
       [t=202] [recipe]    Finished (took 200t)
       [t=202] [failure]   Expected 64× copper plate but found 32×
```

Inputs went in. EU was supplied. Recipe completed normally. The output count is just wrong. That's a recipe definition bug, not a test setup bug.

## Where events come from

Three sources:

1. **Test API methods.** `fillHatch`, `Bus.insert`, `supplyEU`, `assertMachineFormed`, etc. Each one emits at the call site, on success.
2. **The warp differ.** During `Multiblock.runRecipe` or `fastForwardTicks(ticks, watched)`, the framework snapshots each watched controller after every simulated tick and diffs against the previous snapshot. `mProgresstime` going from 0 to positive becomes a `RecipeStarted`; the controller TE vanishing becomes a `MachineExploded`; and so on.
3. **Test lifecycle.** `TestStarted`, `TestFinished`, `AssertionFailed`, `IsolationViolation` come from the runner.

The differ only watches controllers you register. It does not poll the whole warp region. If you call `fastForwardTicks(1000)` with no watched list, you get no recipe events for that warp. `Multiblock.runRecipe` registers its controller automatically.

## Catalog

Records live in `com.gtnewhorizons.gametest.api.event`. All are Java records, all carry a `tick` and a one-line `summary()`.

### Lifecycle

`TestStarted`, `TestFinished`, `StructurePlaced`, `WarpStarted`, `WarpFinished`.

`WarpFinished.stopReason` is one of `completed`, `predicate`, `timeout`. `predicate` means the warp's stop condition fired (typical for `runRecipe` ending when the machine goes idle). `timeout` means the warp ran its full `maxTicks` without the predicate triggering.

### Structure

| Record | When it fires |
|---|---|
| `MachineFormed` | `assertFormed` succeeds, or the differ sees `mMachine` flip to true |
| `MachineDeformed` | `mMachine` flipped to false, controller TE still present |
| `MachineExploded` | Controller TE no longer exists, or `assertMachineExploded` passes |
| `MachineDisabled` | Active machine stops without finishing a recipe |
| `StructureCheckRan` | `assertFormed` had to call `checkStructure(true)` as a fallback |

`MachineFormed.cause` has three values. The distinction matters because "I built this structure inside the test" and "this structure was already built when the test started" usually point at different bugs:

- `OBSERVED_ON_FIRST_POLL`: the controller already reported formed the first time we polled it. Usually the structure template loaded a pre-formed multi.
- `FORCED_BY_ASSERTION`: `assertFormed` found `mMachine == false`, ran `checkStructure(forceReset=true)`, and the machine then formed. A `StructureCheckRan` event sits right before this one.
- `FORMED_DURING_WARP`: the controller flipped to formed mid-warp. Rare. Usually a delayed block update.

### Recipe

| Record | When it fires |
|---|---|
| `RecipeStarted` | `mProgresstime` went from 0 to positive |
| `RecipeProgressed` | Progress crossed 25 / 50 / 75 % |
| `RecipeFinished` | Progress reached max and then dropped |
| `RecipeAborted` | Progress dropped before reaching max |
| `RecipeNotFound` | `runUntilMachineIdle` exited and no recipe ever started |
| `TestRecipeInjected` / `TestRecipeRemoved` | `withTestRecipe` injection / end-of-test cleanup |

`RecipeAborted.reason` is the raw `CheckRecipeResult` id from GT (`item_output_full`, `power_overflow`, `no_fuel_found`, …). That string is almost always enough to root-cause the abort.

### Resource

`BusInserted` (bus, itemDisplay, count), `HatchFilled` (hatch, fluidName, amountMb, accepted), `ProgrammedCircuitSet` (bus, config).

### Energy

`EUSupplyJobRegistered` (hatch, voltage, amperage, durationTicks) fires whenever you call `supplyEU` / `Hatch.supply`. `EUBufferOverflow` fires when a supply job tries to push more EU than the hatch has room for; you usually do not want to see this in a test log. `HatchVoltageMismatch` is emitted alongside an explosion when the last supply job exceeded the hatch tier.

### Maintenance

`MaintenanceIssueAppeared` fires when the differ sees one of the six tool flags flip from OK to broken. `issueType` is the tool name: `WRENCH`, `SCREWDRIVER`, `SOFT_MALLET`, `HARD_HAMMER`, `SOLDERING_TOOL`, `CROWBAR`. `MaintenanceFixed` fires from `fixAllMaintenanceIssues` and `Multiblock.fixMaintenance`.

### Diagnostic

`PollutionEmitted` (originChunk, amount, cumulative), `CleanroomEfficiencyChanged` (controller, efficiencyTenThousandths in 0–10000 = 0.00–100.00 %). `EventOverflow` shows up at most once per test, if you somehow blow past the 10 000-event cap.

### Failure

`AssertionFailed` (message, throwableType, failPos) and `IsolationViolation` (culprit, landedAtAbs, detail). Both also show up in the existing `<failure>` and `<error>` JUnit elements; the event-log copy gives them a tick position relative to the rest of the run.

## Quirks worth knowing

The differ snapshots `mProgresstime` once per simulated tick. A few things follow:

- An "instant" recipe that starts and finishes inside a single tick shows up as one `RecipeFinished` with no preceding `RecipeStarted`, because the differ never sees a non-zero progress value. If that surprises you, check the recipe duration.
- `RecipeFinished` and `RecipeAborted` are distinguished by whether the last observed progress was within one tick of max. A recipe that naturally hits max but whose post-processing fails the next tick produces `RecipeFinished` followed by an explosion or deformation, not `RecipeAborted`.
- Maintenance is bitmask-diffed. If three flags flip in the same tick you get three `MaintenanceIssueAppeared` events in deterministic order (wrench, then screwdriver, then soft mallet, …).
- The clock advances inside the warp loop. Events emitted from inside `runRecipe` get tick values much larger than the wall-clock tick of the surrounding `@GameTest` method. That is intentional: it means a recipe that started 50 ticks into a 200-tick warp shows `t=50`, not `t=0`.

## Programmatic access

The same log is reachable from inside a test:

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

When recording is disabled, every emit site is a `Supplier` that never gets called; nothing allocates. When it is enabled, the per-tick cost during a warp is one adapter call per watched controller (six int reads plus a comparison) and one allocation only when something actually changed. A 200-tick recipe typically produces around five events. A 20 000-tick fusion run produces around ten.

If you ever hit the 10 000-event cap, the framework is being used in a way it was not designed for. Please file an issue.

## When GT fields change

All GT-internal reads go through `GT5UnofficialAdapter`. If GT5 renames `mProgresstime`, splits `MTEMultiBlockBase`, or reshapes `CheckRecipeResult`, exactly one file fails to compile. Event records do not import `gregtech.*` directly.
