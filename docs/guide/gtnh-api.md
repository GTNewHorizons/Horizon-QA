---
title: GTNH multiblock API
description: Drive GregTech multiblocks from tests via Multiblock, hatches, EU supply, time-warp, and scoped recipes.
tags:
  - guides
  - gtnh
---

# GTNH multiblock API

`helper.gtnh()` returns `GTNHGameTestHelper`, the GregTech-facing entry point. Prefer **`Multiblock`** for readable tests; the imperative helpers are appropriate when prototyping or when no hatch roles are mapped yet.

## Multiblock roles

```java
import static com.gtnewhorizons.gametest.api.TestPos.at;

Multiblock ebf = helper.gtnh().multiblock(at(1, 0, 0)); // (1)!
ebf.assertFormed();
ebf.fixMaintenance(); // (2)!

ebf.inputBus(0)
    .insert(Materials.Nickel.getDust(1), Materials.Aluminium.getDust(3))
    .programmedCircuit(0);

ebf.energyHatch(0).supply(TierEU.EV, 1, 900); // (3)!

ebf.runRecipe(); // (4)!

ebf.outputs().assertContains(Materials.NickelAluminide.getIngots(4));
```

1.  Controller position, test-relative. The `Multiblock` façade resolves hatch roles from the template's tile entity table.
2.  Maintenance gates recipes even when EU and inputs are present. Most tests want it satisfied up front.
3.  Voltage × amperage × duration in ticks. Over-tier supply explodes hatches — that is intentional, and the event log records the cause.
4.  Time-warps until the controller reports idle, auto-registering it with the recorder so recipe events appear in the log.

Hatch indices are **logical roles** defined by your template layout, not world coordinates. Re-export the template if you change hatch ordering.

## Time-warp

Long recipes are not waited out at 20 TPS. The framework **simulates ticks** inside a warp region:

| API                                          | Behavior                                                                              |
|----------------------------------------------|---------------------------------------------------------------------------------------|
| `ebf.runRecipe()`                            | Warp until the controller is idle; auto-registers the controller for event diffing    |
| `gtnh.runUntilMachineIdle(controller, max)`  | Imperative equivalent                                                                 |
| `gtnh.fastForwardTicks(n)`                   | Advance `n` simulated ticks; **no recipe events** unless controllers are watched      |

!!! note "Event ticks inside a warp"

    Events inside a warp use **simulated** tick counters — a 200-tick recipe shows `t=200` even though the warp completed in milliseconds. See [Test event log](../reference/events.md).

## EU supply

```java
ebf.energyHatch(0).supply(TierEU.EV, 1, durationTicks);
// or, imperatively:
gtnh.supplyEU(hatchPos, voltage, amperage, durationTicks);
```

Supply satisfies preconditions; it does not replace recipe validation. Over-tier supply explodes hatches — see `HatchVoltageMismatch` and `EUBufferOverflow` in the event log.

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

Cleanup is registered via `afterTest` — no try-with-resources required. Injection and end-of-test removal emit `TestRecipeInjected` and `TestRecipeRemoved`. See [Design principle 6 — Leave no trace](../contributing/principles.md).

## GT internals boundary

Every direct GT field read goes through `GT5UnofficialAdapter`. If GT5 renames an internal field, exactly one file fails to compile; event record types do not import `gregtech.*`. GT bumps are handled in that one class.

## Javadoc

Generated API: [`GTNHGameTestHelper` and `Multiblock`](https://GTNewHorizons.github.io/Horizon-QA/javadoc/index.html).
