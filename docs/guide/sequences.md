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

## Setup code and startSequence

The test body itself runs during tick START. Code placed before `startSequence()` executes
in the same tick and phase as a leading `thenExecuteAtStart`, so both patterns are equivalent:

=== "Setup before `startSequence`"

    ```java
    @GameTest(template = "stone_platform")
    public static void example(GameTestHelper helper) {
        helper.setBlock(0, 1, 0, Blocks.chest);          // runs at tick START
        helper.startSequence()
            .thenWaitUntil(() -> helper.assertBlockPresent(helper.absolute(0, 1, 0), Blocks.chest))
            .thenSucceed();
    }
    ```

=== "Setup inside `thenExecuteAtStart`"

    ```java
    @GameTest(template = "stone_platform")
    public static void example(GameTestHelper helper) {
        helper.startSequence()
            .thenExecuteAtStart(() -> helper.setBlock(0, 1, 0, Blocks.chest))  // also tick START
            .thenWaitUntil(() -> helper.assertBlockPresent(helper.absolute(0, 1, 0), Blocks.chest))
            .thenSucceed();
    }
    ```

The only difference is that `thenExecuteAtStart` defers the action to the **next** tick, while
inline code runs immediately during test setup. In practice this has no observable effect because
no world update happens between them. Use whichever reads better for the test at hand.

!!! tip
    Keep structural setup (placing blocks, configuring machines) before `startSequence()` and
    reserve the sequence itself for the stimulus/response steps. This keeps the sequence focused
    on what the test is actually verifying.

## thenIdle counts ticks, not gaps

`thenIdle(1)` means exactly one full tick separates the event before it from the event after.

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
