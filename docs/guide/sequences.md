---
title: Sequences & timing
description: GameTestSequence for ordered steps, delays, and bounded waits without real-time sleeps.
tags:
  - guides
  - timing
---

# Sequences & timing

`GameTestSequence` schedules actions on future test ticks. Use it when a test needs to do something, wait a bit, then check the result.

## Basic usage

```java
@GameTest(template = "stone_platform", timeoutTicks = 60)
public static void delayedAssert(GameTestHelper helper) {
    helper.startSequence()
        .thenIdle(10)
        .thenExecute(() -> helper.assertBlockPresent(helper.absolute(0, 0, 0), Blocks.stone))
        .thenSucceed();
}
```

One sequence per test. For an immediate pass use `helper.succeed()` directly.

## Methods

| Method                                     | Phase | What it does                                    |
|--------------------------------------------|-------|-------------------------------------------------|
| `thenIdle(n)`                              |       | Skip `n` ticks                                  |
| `thenExecute(Runnable)`                    | END   | Run once at scheduled tick                      |
| `thenExecuteAtStart(Runnable)`             | START | Run once at scheduled tick, before world tick   |
| `thenExecuteAtEnd(Runnable)`               | END   | Alias of `thenExecute`                          |
| `thenExecuteFor(n, Runnable)`              | END   | Run every tick for `n` ticks                    |
| `thenExecuteForAtStart(n, Runnable)`       | START | Same, before world tick                         |
| `thenExecuteForAtEnd(n, Runnable)`         | END   | Alias of `thenExecuteFor`                       |
| `thenWaitUntil(Runnable)`                  | END   | Retry each tick until runnable does not throw   |
| `thenWaitUntilAtStart(Runnable)`           | START | Same, before world tick                         |
| `thenWaitUntil(maxTicks, Runnable)`        | END   | Same, advance schedule by `maxTicks` on success |
| `thenWaitUntilAtStart(maxTicks, Runnable)` | START | Same, before world tick                         |
| `thenSucceed()`                            | END   | Pass the test                                   |
| `thenFail(msg)`                            | END   | Fail the test                                   |

## Tick phases

Every server tick has a START phase and an END phase. World logic (tile entity updates, hopper transfers, machine processing) runs between them.

Default sequence methods run at END, so assertions see the world after it has ticked. Use the `AtStart` variants to deliver input before the world ticks, which is what you want when testing machines that consume their input during the tick.

```java
// insert at START so the machine sees it during the tick, assert at END
helper.startSequence()
    .thenExecuteAtStart(() -> ioport.setInventorySlotContents(0, cell.copy()))
    .thenExecute(() -> {
        helper.assertNull(ioport.getStackInSlot(0));
        helper.assertTrue(ItemStack.areItemStacksEqual(ioport.getStackInSlot(6), cell));
    })
    .thenSucceed();
```

### Phase ordering

Within one scheduled tick, START must come before END. The builder throws `IllegalStateException` if you try to go the other way:

```java
helper.startSequence()
    .thenExecute(assertion)
    .thenExecuteAtStart(stimulus);  // throws -- can't go back to START
```

Use `thenIdle(1)` to push the START event to the next tick:

```java
helper.startSequence()
    .thenExecute(assertion)
    .thenIdle(1)
    .thenExecuteAtStart(nextStimulus);
```

## thenIdle counts ticks, not gaps

`thenIdle(1)` means exactly one full tick separates the event before it from the event after. Previously (before the phase model), idle created a gap of `N-1` ticks due to 1-based tick indexing. Tests will run one tick longer than before, which rarely matters since most use generous timeouts.

## Assertions inside thenExecute

Throw any `AssertionError` subclass. It propagates through the sequence and fails the test with the usual position context.

## Prefer state polling over fixed delays

```java
.thenWaitUntil(500, () -> gtnh.assertMachineFormed(controller))
```

is more reliable than `thenIdle(fixedRecipeLength)`. See [Design principle 4](../contributing/principles.md).

## Interaction with warp

`runRecipe` and `fastForwardTicks` advance the event recorder clock, not the sequence scheduler. The two clocks are independent. When reading timing logs, check which clock an event came from before drawing conclusions.

## Choosing the right tool

|                    | Use when                                                   |
|--------------------|------------------------------------------------------------|
| `onEachTick`       | Something must hold true on every tick until the test ends |
| `GameTestSequence` | Steps need to happen in order with delays between them     |
| `succeedWhen`      | Waiting for a single condition to become true              |
