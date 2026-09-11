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
| `absolute(TestPos pos)` | World position for a test-local `TestPos` |
| `absolute(String label)` | World position for a rotation-aware template label |
| `getOriginX/Y/Z()` | Absolute origin of the current test cell |

Coordinate-based helper methods consistently accept raw test-local coordinates, a test-local `TestPos`, or a structure label. Range-based entity helpers accept either two `TestPos` values or two labels. Label overloads resolve rotation before delegating, so assertion failures retain the same world position as their raw-coordinate counterparts.

Use `pos("label")` when a position must be stored or passed to another API. Use `absolute(...)` only when calling a Minecraft or mod API that explicitly expects world coordinates.

## Completion and scheduling

| Method | Use |
|---|---|
| `succeed()` | Pass immediately after synchronous assertions |
| `succeedWhen(BooleanSupplier)` | Poll a positive condition once per test tick |
| `succeedAtTimeout()` | Pass only after the full timeout window, usually for negative invariants |
| `assumeTrue(boolean, String)` | Skip immediately when a runtime precondition is false |
| `assumeFalse(boolean, String)` | Skip immediately when a runtime precondition is true |
| `TickCallbackHandle onEachTick(String, Runnable)` | Register a named, initially enabled invariant or observer |
| `TickCallbackHandle onEachTickDisabled(String, Runnable)` | Register a named callback for a later sequence window |
| `startSequence()` | Build one ordered sequence of START/END actions and waits |
| `afterTest(Runnable)` | Register cleanup that runs on pass, skip, failure, timeout, or error |
| `afterTestAsync(int, Supplier<? extends CompletionStage<?>>)` | Register one bounded asynchronous teardown before ordinary cleanup |
| `getTestId()` | Obtain the selected test id including its parameter case suffix |
| `recordDiagnostic(String)` | Append a report line from the server test thread |

For [client scenarios](../guide/ci.md#automated-client-tests), use `ClientTest.scenario(helper)` to create a fluent chain backed by one existing sequence and one client session. It owns the asynchronous teardown slot. Do not also call `startSequence()` or attach a second client session for that test. Register client cleanup with `scenario.afterTest(c -> ...)`. Ordinary `helper.afterTest(...)` cleanup remains on the server thread after client teardown.

See [Sequences and timing](../guide/sequences.md) for phase ordering and bounded waits.

A failed assumption aborts the current method or callback and records its message as the skip reason. Cleanup registered with `afterTest` still runs. Use holder-level `requiredMods` for optional class dependencies that must be checked before class loading; use assumptions for runtime state such as configuration, registrations, or capabilities that discovery cannot know.

Both methods require a non-blank diagnostic name. Callback failures retain their original throwable and
position while reports identify the named callback. Call `disable()` to pause the callback, `enable()` to
resume it, or `remove()` to unregister it permanently. These operations are idempotent; enabling or
disabling a removed handle has no effect. Actual registration and state changes appear in the test event
log. See [Scoped sequence windows](../guide/negative-tests.md#scoped-sequence-windows).

## Client scenarios

`ClientTest.scenario(helper)` provides the default client authoring surface in `com.gtnewhorizons.horizonqa.api.client`:

| Task | Methods |
|---|---|
| Name a reusable live lookup | `ClientTarget.of(description, resolver)` |
| Send native input | `useHeldItem`, `click`, `rightClick`, `shiftClick`, `scroll`, `key`, `escape` |
| Describe a drag | `drag(target).to(endpoint).overFrames(n)`, with optional `button(index)` |
| Wait for a screen | `awaitScreen(type)` |
| Run custom work | `client(label, action)`, `server(label, action)` |
| Retry custom assertions | `awaitClient(label, assertion)`, `awaitServer(label, assertion)` |
| Accelerate a server-state wait | `awaitServerAccelerated(label, multiplier, assertion)` |
| Compose completion stages | `async(label, action)`, `awaitAsync(label, assertion)` |
| Configure the next step | `step(label)`, `withinTicks(n)` |
| Change subsequent bounded-step defaults | `defaultTimeoutTicks(n)`, initially 100 ticks |
| Retain a rendered checkpoint | `capture(name)` |
| Register client cleanup | `afterTest(cleanup)` |
| Access existing phase scheduling | `serverSequence()`, returning the same sequence |
| Finish authoring | `succeed()` |

A `ClientTarget` stores a description and a resolver without reading the GUI. During execution the resolver returns a `ClickTarget` observation containing stable identity, clipped visible bounds and actual hit-test readiness. Return `null` while unavailable and throw for ambiguity.

`client` callbacks run on the client thread. `server`, `async` and `awaitAsync` callbacks start on the server thread, so custom asynchronous client work must use the supplied session's queued methods. Bounded operations include queue time in their budget. `withinTicks` does not apply to synchronous `server` actions.

Low-level `ClientTest.attach(helper)` remains available for direct sequence authoring, and the same low-level methods are available in custom scenario callbacks. This advanced surface supports specialized future composition and input operations without adding another executor or cleanup lifecycle.

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
| Count items | `countItems` |
| Set up or clear a specific slot | `setSlot`, `clearSlot` |
| Assert inventory contents | `assertInventoryContains`, `assertInventoryCount`, `assertInventoryEmpty`, `assertSlot` |
| Insert or inspect fluid | `insertFluid`, `assertFluidTank`, `assertFluidTankEmpty` |

`countItems` and `assertInventoryCount` sum matching item, damage, and NBT across every slot while ignoring the template stack size. Exact counts may be zero. `setSlot` and `clearSlot` bypass normal sided insertion and extraction rules for fixture setup and mark the inventory dirty after mutation.

When a test already has an `IInventory`, the corresponding low-level operations are `InventoryHelper.count`, `InventoryHelper.setSlot`, and `InventoryHelper.clearSlot`.

`assertInventoryContains` honors the expected `ItemStack` size as a minimum. The fluent GregTech `Bus.assertContains(ItemStack)` API matches item identity and metadata but ignores stack size; use `ItemMatcher.count(...)` there when quantity matters.

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
