---
title: Core helper API
description: Task-oriented map of GameTestHelper coordinates, completion, assertions, world fixtures, inventories, entities, and diagnostics.
---

# Core helper API

`GameTestHelper` is passed to every `@GameTest` method. This page maps common tasks to method families. It is intentionally shorter than the generated [Javadoc](https://www.gtnewhorizons.com/Horizon-QA/javadoc/com/gtnewhorizons/horizonqa/api/GameTestHelper.html), which remains the source for every overload and parameter.

## Coordinates

| Method | Result |
|---|---|
| `pos(String label)` | Rotation-aware, test-local position from the structure template |
| `absolute(int x, int y, int z)` | World position for a raw test-local coordinate |
| `absolute(String label)` | World position for a rotation-aware template label |
| `getOriginX/Y/Z()` | Absolute origin of the current test cell |

Most helper methods take test-local coordinates. Use `pos("label")` with them. Use `absolute(...)` only when calling a Minecraft or mod API that explicitly expects world coordinates.

## Completion and scheduling

| Method | Use |
|---|---|
| `succeed()` | Pass immediately after synchronous assertions |
| `succeedWhen(BooleanSupplier)` | Poll a positive condition once per test tick |
| `succeedAtTimeout()` | Pass only after the full timeout window, usually for negative invariants |
| `onEachTick(Runnable)` | Run an invariant or observer on every test tick |
| `startSequence()` | Build one ordered sequence of START/END actions and waits |
| `afterTest(Runnable)` | Register cleanup that runs on pass, failure, timeout, or error |

See [Sequences and timing](../guide/sequences.md) for phase ordering and bounded waits.

## General assertions

The helper includes familiar assertion families:

- `assertTrue`, `assertFalse`, `assertNull`, and `assertNotNull`
- `assertEquals` and `assertNotEquals` for objects, integers, and floating-point values with a delta
- `assertItemEqual` for exact `ItemStack` equality, including item ID, damage, stack size, and NBT
- `assertSame`, `assertNotSame`, and `assertInstanceOf`
- `assertThrows`
- `assertIterableEquals`
- `assertLinesMatch` for exact lines, regular expressions, and skip directives
- `fail`

Prefer overloads with a useful message when the default output does not explain the expected and observed state.

Use `assertItemEqual(expected, actual)` when every part of an item stack matters. A mismatch prints the item registry ID, damage, stack size, and NBT for both stacks, or `null` when either value is absent:

```java
helper.assertItemEqual(expectedOutput, outputBus.getStackInSlot(0),
    "Crafting output mismatch");
```

The examples mod includes a focused [`itemStackEqualityIncludesNbt`](https://github.com/GTNewHorizons/Horizon-QA/blob/master/examples/src/main/java/com/gtnewhorizons/horizonqa/examples/tests/HelperApiTests.java) runnable case.

## Blocks and tile entities

| Task | Methods |
|---|---|
| Place or remove blocks | `setBlock`, `destroyBlock` |
| Assert block and metadata | `assertBlockPresent`, `assertBlockAbsent` |
| Find a tile entity | `assertTileEntityPresent` |
| Read or update tile NBT | `getTileNBT`, `setTile`, `assertTileNBT`, `assertTileNBTPath` |

All coordinates in these methods are test-local.

## Inventories and fluids

| Task | Methods |
|---|---|
| Insert or extract an item | `insertItem`, `extractItem` |
| Assert inventory contents | `assertInventoryContains`, `assertInventoryEmpty`, `assertSlot` |
| Insert or inspect fluid | `insertFluid`, `assertFluidTank`, `assertFluidTankEmpty` |

`assertInventoryContains` honors the expected `ItemStack` size. The fluent GregTech `Bus.assertContains(ItemStack)` API matches item identity and metadata but ignores stack size; use `ItemMatcher.count(...)` there when quantity matters.

## Entities and players

| Task | Methods |
|---|---|
| Query entities | `getEntities` |
| Assert presence, absence, or count | `assertEntityPresent`, `assertEntityAbsent`, `assertEntityCount` |
| Spawn an entity | `spawnEntity` |
| Inspect entity NBT | `getEntityNBT`, `assertEntityNBT`, `assertEntityNBTPath` |
| Simulate player interaction | `spawnFakePlayer`, `simulateRightClick`, `simulateLeftClick` |

Entity positions and bounding boxes are test-local. Spawned fake players should be treated as fixture state and kept within the test boundary.

## Redstone and world controls

- `setRedstoneInput`, `pulseRedstone`, and `assertRedstonePower`
- `disableRandomTicks`
- `fixWorldTime`
- `setWeather`
- `getWorld()` for an operation not covered by the helper

When using `getWorld()`, convert a local position exactly once with `helper.absolute(...)`.

## Diagnostics and GTNH

`getRecorder()` returns the typed per-test event log. Use it when an assertion naturally depends on history, such as the number of completed recipes.

`gtnh()` returns `GTNHGameTestHelper`, which adds multiblock, hatch, EU supply, time-warp, maintenance, and temporary recipe helpers. See [GTNH multiblock API](../guide/gtnh-api.md).
