---
title: GTNH multiblock API
description: Drive GregTech multiblocks from tests via Multiblock, hatches, EU supply, time-warp, and scoped recipes.
tags:
  - guides
  - gtnh
---

# GTNH multiblock API

`helper.gtnh()` returns `GTNHGameTestHelper`, the GregTech-facing entry point. Two styles drive the same machinery:

| Style                                  | Reach for it when                                                                  |
|----------------------------------------|-------------------------------------------------------------------------------------|
| Fluent (`Multiblock` and its hatches)  | The default. The template has hatch roles and the test should read as a scenario   |
| Imperative (`GTNHGameTestHelper`)      | Prototyping, one-off calls, or positions not yet mapped to roles                   |

Both appear side by side in [Writing tests](writing-tests.md#imperative-vs-fluent-gt-api). Tests that settle on the fluent style survive template re-exports and rotations better, because positions stay out of the test body.

## Multiblock roles

```java
Multiblock ebf = helper.gtnh().multiblock(helper.pos("controller")); // (1)!
ebf.assertFormed();
ebf.fixMaintenance(); // (2)!

ebf.inputBus(0)
    .insert(Materials.Nickel.getDust(1), Materials.Aluminium.getDust(3))
    .programmedCircuit(0);

ebf.energyHatch(0).supply(TierEU.EV, 1, 900); // (3)!

ebf.runRecipe(); // (4)!

ebf.outputs().assertContains(Materials.NickelAluminide.getIngots(4));
```

1.  Controller label from the template. `helper.pos(...)` returns test-relative coordinates and applies structure rotation.
2.  Maintenance gates recipes even when EU and inputs are present. Most tests want it satisfied up front.
3.  Voltage × amperage × duration in ticks. Over-tier supply explodes hatches. That is intentional, and the event log records the cause.
4.  Time-warps until the controller reports idle, auto-registering it with the recorder so recipe events appear in the log.

Hatch indices are **logical roles** defined by your template layout, not world coordinates. Label load-bearing coordinates in the template and re-export it if you change hatch ordering.

## Time-warp

Long recipes are not waited out at 20 TPS. The framework **simulates ticks** inside a warp region:

| API                                          | Behavior                                                                              |
|----------------------------------------------|---------------------------------------------------------------------------------------|
| `ebf.runRecipe()`                            | Warp until the controller is idle; auto-registers the controller for event diffing    |
| `gtnh.runUntilMachineIdle(controller, max)`  | Imperative equivalent                                                                 |
| `gtnh.fastForwardTicks(n)`                   | Advance `n` simulated ticks; **no recipe events** unless controllers are watched      |

!!! note "Event ticks inside a warp"

    Events inside a warp use **simulated** tick counters: a 200-tick recipe shows `t=200` even though the warp completed in milliseconds. See [Test event log](../reference/events.md).

## EU supply

```java
ebf.energyHatch(0).supply(TierEU.EV, 1, durationTicks);
// or, imperatively:
gtnh.supplyEU(hatchPos, voltage, amperage, durationTicks);
```

Supply satisfies preconditions; it does not replace recipe validation. Over-tier supply explodes hatches; see `HatchVoltageMismatch` and `EUBufferOverflow` in the event log.

## Maintenance

```java
ebf.fixMaintenance();
gtnh.assertMachineHasIssues(controller, MaintenanceType.WRENCH);
```

Maintenance issues gate recipes even when inputs and EU are present. The canonical example is `maintenanceGatesRecipeEvenWithFullSupply` in the examples mod.

## Fluids and buses

```java
gtnh.fillHatch(pos, "nitrogen", 2000);
gtnh.assertFluidInHatch(pos, "nitrogen", 2000);
```

Bus insert and assert helpers exist symmetrically on `GameTestHelper` and on `Multiblock` bus groups.

For negative item assertions, use `assertNotContains`:

```java
ebf.outputBus(0).assertNotContains(Materials.Gold.getIngots(1));
ebf.outputs().assertNotContains(Materials.Gold.getIngots(1));
```

For fixture setup, a `Bus` can write slots directly. This bypasses normal insertion rules, so it is useful for cases
like pre-filling an output bus:

```java
ebf.outputBus(0).fillAllSlots(Materials.Stone.getDust(64));
ebf.outputBus(0).setSlot(0, Materials.Stone.getDust(64));
```

Use `insert(...)` when the test should simulate normal insertion behavior.

## Structure checks

`assertFormed()` is the normal positive assertion. For invalid-template tests, use the negative helpers:

```java
Multiblock ebf = helper.gtnh().multiblock(helper.pos("controller"));
ebf.assertNeverForms("EBF formed without coils");
```

`assertNeverForms(...)` forces a structure check immediately, asserts the machine is unformed on every tick, and
succeeds at timeout. If you mutate a valid template inside Java, use `assertNotFormed(...)` or
`forceStructureCheck()` to invalidate stale controller state before reading `isFormed()`.

## Synthetic recipes

Register a temporary recipe for the rest of the test; it is removed automatically when the test ends:

```java
GTRecipeBuilder synthetic = GTValues.RA.stdBuilder()
    .itemInputs(Materials.Lead.getDust(1))
    .itemOutputs(Materials.Gold.getIngots(1))
    .duration(200)
    .eut(TierEU.EV);

gtnh.withTestRecipe(ebf, synthetic);
// Lead → gold for this test only; global recipe maps remain untouched.
```

Cleanup is registered via `afterTest`; no try-with-resources required. Injection and end-of-test removal emit `TestRecipeInjected` and `TestRecipeRemoved`. See [Design principle 6, "Leave no trace"](../contributing/principles.md).

## GT internals boundary

Every direct GT field read goes through `GT5UnofficialAdapter`. If GT5 renames an internal field, exactly one file fails to compile; event record types do not import `gregtech.*`. GT bumps are handled in that one class.

When the facade does not cover an exotic case yet, `GTNHGameTestHelper` exposes low-level escape hatches:

```java
gtnh.gtTile(pos);
gtnh.metaTileEntity(pos);
gtnh.multiBlockController(pos);
```

Prefer role-based methods first; these are intentionally closer to GregTech internals.

## Javadoc

Generated API: [`GTNHGameTestHelper` and `Multiblock`](https://GTNewHorizons.github.io/Horizon-QA/javadoc/index.html).
