---
title: Test event log
description: Emitted event types, trace timing, JUnit output, warp differ behavior, and programmatic access.
---

# Test event log

Every test owns an ordered log of typed events. Reported batches write that log to the test case's JUnit `<system-out>`. The CI console summary also prints the last 20 trace lines for a failed case.

Interactive failures are logged immediately, but they do not print the same 20-line console tail. Use the in-world cell and `helper.getRecorder()` when investigating an interactive run.

Set `-Dhorizonqa.events=off` on the server JVM to disable recording. Event suppliers are not evaluated while recording is disabled, so payload work is skipped.

## Line format and clock

Reporters format each entry as:

```text
[t=  201] [recipe     ] Recipe finished at TestPos{x=256, y=64, z=256} (took 200t)
```

The clock is logical test time:

- Normal server test ticks advance it once at START.
- Every simulated time-warp tick also advances it once.
- Wall-clock time is not represented.

The sequence scheduler and time-warp use different loops, even though both contribute to the recorder clock.

## Representative trace

```text
[t=    0] [lifecycle  ] Placed template 'mymod:ebf' at TestPos{x=256, y=64, z=256} (5×4×5)
[t=    0] [lifecycle  ] Test mymod:EbfTests.smelt started at TestPos{x=256, y=64, z=256}
[t=    0] [structure  ] MTEElectricBlastFurnace formed at TestPos{x=258, y=64, z=258} (OBSERVED_ON_FIRST_POLL, 2ib/1ob/1eh)
[t=    0] [resource   ] Inserted 1× Nickel Dust into TestPos{x=257, y=64, z=258}
[t=    0] [energy     ] EU supply job: 1920 EU/t × 1 A for 900t into TestPos{x=259, y=64, z=258}
[t=    0] [lifecycle  ] Time-warp started (maxTicks=1500, watching 1 controller(s))
[t=    1] [recipe     ] Recipe started at TestPos{x=258, y=64, z=258} (1920 EU/t × 200t, 1p)
[t=  201] [recipe     ] Recipe finished at TestPos{x=258, y=64, z=258} (took 200t)
[t=  201] [lifecycle  ] Time-warp finished after 201 simulated tick(s) (predicate)
```

Read the trace in order: fixture, setup, supply, processing, and result. Absence is useful too. If a warp finishes without `RecipeStarted`, the controller never exposed an active recipe during that observed window.

## Event sources

Events come from three places:

1. **Author-facing helpers** record successful setup or assertion-related actions such as bus insertion, fluid filling, EU job registration, maintenance repair, and temporary recipe changes.
2. **The warp differ** snapshots watched controllers and emits formation, recipe, maintenance, and explosion transitions.
3. **The test instance and runners** record test start, failure, finish, and supported isolation failures. The reported batch runner also records successful structure placement.

`Multiblock.runRecipe` watches its controller automatically. `fastForwardTicks(n)` does not watch any controller unless the absolute positions are passed to its overload.

## Emitted event catalog

This catalog lists event records with active emit sites in the current implementation. Other record classes may exist in the API package for future work, but tests should not expect them until they are listed here.

### Lifecycle

| Record | Emitted when |
|---|---|
| `StructurePlaced` | The reported batch runner places a template successfully |
| `TestStarted` | The test body is about to run |
| `WarpStarted` | A time-warp begins |
| `WarpFinished` | A time-warp ends |
| `TestFinished` | Cleanup has run and the final status is known |

`WarpFinished.stopReason` is:

- `completed` when a fixed warp runs its full length.
- `predicate` when a stop condition succeeds before the bound.
- `timeout` when a conditional warp reaches the bound.

### Structure and safety

| Record | Emitted when |
|---|---|
| `MachineFormed` | `assertFormed` succeeds or a watched controller becomes formed |
| `MachineDeformed` | A watched formed controller becomes unformed, including after an observed explosion |
| `MachineExploded` | A watched controller tile disappears, or `assertMachineExploded` succeeds |
| `StructureCheckRan` | The fluent facade calls `checkStructure(...)` |

`MachineFormed.cause` distinguishes a controller already formed at the first poll, a forced assertion check, and formation during a warp.

Explosion events currently use an `UNKNOWN` cause. Do not infer hatch-tier or cable behavior from that value.

### Recipe

| Record | Emitted when |
|---|---|
| `RecipeStarted` | Watched progress changes from idle to active |
| `RecipeProgressed` | Watched progress crosses 25%, 50%, or 75% |
| `RecipeFinished` | Active progress reaches its end and returns to idle |
| `RecipeAborted` | Active progress returns to idle before its expected end |
| `TestRecipeInjected` | `withTestRecipe` adds a temporary recipe |
| `TestRecipeRemoved` | Cleanup removes that temporary recipe |

`RecipeAborted.reason` is the `CheckRecipeResult` ID observed after the stop when one is available. Treat it as diagnostic context, not a complete causal model.

### Resources and energy

| Record | Emitted when |
|---|---|
| `BusInserted` | A fluent bus insert succeeds |
| `HatchFilled` | A fluent or imperative fluid fill succeeds |
| `ProgrammedCircuitSet` | A circuit is written through a helper |
| `EUSupplyJobRegistered` | Virtual EU supply is scheduled |
| `EUBufferOverflow` | A scheduled job attempts another push while the hatch buffer is already at capacity |

Virtual EU supply directly fills the buffer during simulated ticks. It does not model packet-tier rejection, cable loss, or hatch explosions.

### Maintenance and diagnostics

| Record | Emitted when |
|---|---|
| `MaintenanceIssueAppeared` | A watched controller gains a new maintenance issue during a warp |
| `MaintenanceFixed` | A maintenance helper repairs a controller |
| `PollutionEmitted` | `assertPollutionEmitted` succeeds and records the measured amount |
| `CleanroomEfficiencyChanged` | `assertCleanroomStatus` succeeds and records the observed efficiency |
| `EventOverflow` | The per-test event cap is reached |

An issue that already exists in the pre-warp snapshot does not emit `MaintenanceIssueAppeared`; the event represents a transition.

### Failure

| Record | Emitted when |
|---|---|
| `AssertionFailed` | A started test instance fails from an assertion, exception, or in-test infrastructure failure |
| `IsolationViolation` | Cleanup finds a GregTech tile entity in the protected outer cell margin |

The isolation scanner also adds warnings for non-air blocks outside a placed template's footprint. It does not inspect arbitrary entities, fluids, fake players, world rules, or global registries.

## Differ behavior

The warp differ compares one snapshot per watched controller after each simulated tick.

- A recipe that begins and finishes between observable snapshots may emit `RecipeFinished` without a preceding `RecipeStarted`.
- A progress drop at least one tick before the expected maximum is classified as `RecipeAborted`; a drop at the end is classified as `RecipeFinished`.
- Multiple newly broken maintenance flags in one tick produce multiple events in fixed tool order.
- If a watched controller tile disappears, the differ emits `MachineExploded` and, if it was formed, `MachineDeformed`.

## Programmatic access

Use the typed log when an assertion is naturally about history:

```java
@GameTest(template = "ebf")
public static void exactlyOneRecipeFinished(GameTestHelper helper) {
    helper.gtnh()
        .multiblock(helper.pos("controller"))
        .runRecipe();

    long finished = helper.getRecorder()
        .snapshot()
        .stream()
        .filter(RecipeFinished.class::isInstance)
        .count();

    helper.assertEquals(1L, finished);
    helper.succeed();
}
```

`snapshot()` returns an unmodifiable list in emission order. Pattern-match or cast to a concrete record type to read its payload.

## Cost and limits

With recording disabled, `record(...)` returns without evaluating the supplied event factory.

With recording enabled, watched warps pay for controller snapshots and comparisons on each simulated tick. Event objects are created only when a helper emits one or the differ detects a transition.

The cap is 10,000 events per test. The final slot becomes one `EventOverflow` record and later events are dropped.

Compatibility-sensitive warp snapshots are read through `GT5UnofficialAdapter`. Other facade and recipe-scope code also accesses GregTech APIs directly, so GT updates can affect more than the adapter alone.
