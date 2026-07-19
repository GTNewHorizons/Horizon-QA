---
title: Negative assertions
description: Assert that a bad state never occurs over a tick window, the framework's primary idiom.
---

# Negative assertions

Horizon-QA is aimed at tests that assert a bad state **never** occurs over a tick window. That category covers most of the regressions a traditional unit test will quietly miss.

## Core pattern

```java
@GameTest(template = "ebf_no_coils", timeoutTicks = 60)
public static void doesNotFormWithoutCoils(GameTestHelper helper) {
    Multiblock ebf = helper.gtnh().multiblock(helper.pos("controller"));
    ebf.assertNeverForms("EBF formed without coils");
}
```

For GT multiblock formation tests, `assertNeverForms(...)` forces a structure check immediately, registers a per-tick
negative invariant, and succeeds at timeout. That forced first check matters when you mutate an exported valid template:
the controller may otherwise still hold a stale `mMachine=true` value from placement.

The equivalent explicit pattern must include the same initial structure check:

```java
ebf.assertNotFormed("EBF formed without coils");
helper.onEachTick(() -> helper.assertFalse(ebf.isFormed(), "EBF formed without coils"));
helper.succeedAtTimeout();
```

| Call                                              | Role                                                                  |
|---------------------------------------------------|-----------------------------------------------------------------------|
| `onEachTick(Runnable)`                            | Registers an enabled callback and returns its `TickCallbackHandle`    |
| `assertFalse(ebf.isFormed(), ...)`                | Fails immediately on the tick where the machine forms                 |
| `succeedAtTimeout()`                              | Passes at the END of the final allowed tick if nothing failed         |

The final allowed tick is still observed before `succeedAtTimeout()` passes.

A transient formation fails on **that tick**, not at the end of the window. The framework does not need to wait out the full timeout to report the failure.

## When to use `succeedWhen` instead

Use `succeedWhen(() -> condition)` when you wait for a **positive** eventual state:

```java
TestPos signal = helper.absolute("signal");
helper.succeedWhen(() ->
    helper.getWorld().getBlock(signal.x(), signal.y(), signal.z()) == Blocks.redstone_block);
```

!!! warning "`succeedWhen` is not the right tool for invariants"

    `succeedWhen` exits the moment its predicate becomes true. An invariant that should hold **every** tick wants `onEachTick` + `succeedAtTimeout`, not `succeedWhen`.

## Continuous invariants

Combine multiple checks in a single callback so they share the same window:

```java
helper.onEachTick(() -> {
    helper.assertFalse(ebf.isFormed(), "formed");
    helper.assertFalse(ebf.isProcessing(), "started recipe");
});
helper.succeedAtTimeout();
```

## Scoped sequence windows

Keep the handle returned by `onEachTick` when an invariant applies to only part of a sequence. A handle
starts enabled, so disable it before the first tick when the observation window begins later:

```java
TickCallbackHandle emptyNetworkKeepsCell = helper.onEachTick(() -> {
    helper.assertNotNull(ioport.getStackInSlot(0), "Cell should stay in input");
    helper.assertNull(ioport.getStackInSlot(6), "Cell should not move to output");
});
emptyNetworkKeepsCell.disable();

helper.startSequence()
    .thenWaitUntilAtEnd("IO port network activates", () -> assertActive(ioport))
    .thenExecuteAtStart(() -> {
        ioport.setInventorySlotContents(0, targetCell);
        emptyNetworkKeepsCell.enable();
    })
    .thenIdle(5)
    .thenExecute("finish empty-network observation window", emptyNetworkKeepsCell::remove)
    .thenSucceed();
```

`disable()` pauses the callback and `enable()` resumes it. `remove()` is permanent; later enable or
disable calls have no effect. All three operations are safe to call repeatedly or from a per-tick
callback itself.

Per-tick callbacks run at END before END-phase sequence actions. An enable or disable from a START
action therefore affects the callback later in that same tick. An END action that disables or removes
the handle runs after that tick's callback and affects subsequent ticks.

## Sequences vs. polling

For staged scenarios such as "insert items, then assert no recipe for 40 ticks, then supply EU", combine
a scoped tick callback with [Sequences and timing](sequences.md) instead of maintaining manual tick
counters inside `onEachTick`. Sequences make the ordering and timeout behavior explicit.

## Design alignment

Negative tests implement [Design principle 5, "Negative tests are load-bearing"](../contributing/principles.md). Also see [Principle 4, "Wait on state, not ticks"](../contributing/principles.md): tick counts on `@GameTest` are **timeouts**, never recipe-duration proxies.
