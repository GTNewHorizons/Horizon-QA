---
title: Sequences and timing
description: GameTestSequence for ordered steps, delays, and bounded waits without real-time sleeps.
---

# Sequences and timing

`GameTestSequence` schedules actions on future test ticks. Use it when a test needs to do something, wait a bit, then check the result.

## Basic usage

```java
@GameTest(template = "stone_platform", timeoutTicks = 60)
public static void delayedAssert(GameTestHelper helper) {
    helper.startSequence()
        .thenIdle(10)
        .thenExecute(() -> helper.assertBlockPresent(Blocks.stone, helper.pos("corner")))
        .thenSucceed();
}
```

One sequence per test. For an immediate pass use `helper.succeed()` directly.

## Methods

| Method                                     | Phase | What it does                                  |
|--------------------------------------------|-------|-----------------------------------------------|
| `thenIdle(n)`                              |       | Skip `n` ticks                                |
| `thenExecute(Runnable)`                    | END   | Run once at scheduled tick                    |
| `thenExecuteAtStart(Runnable)`             | START | Run once at scheduled tick, before world tick |
| `thenExecuteAtEnd(Runnable)`               | END   | Alias of `thenExecute`                        |
| `thenExecuteFor(n, Runnable)`              | END   | Run every tick for `n` ticks                  |
| `thenExecuteForAtStart(n, Runnable)`       | START | Same, before world tick                       |
| `thenExecuteForAtEnd(n, Runnable)`         | END   | Alias of `thenExecuteFor`                     |
| `thenWaitUntil(Runnable)`                  | END   | Retry while the callback throws `AssertionError` |
| `thenWaitUntilAtStart(Runnable)`           | START | Same, before world tick                       |
| `thenWaitUntilAtEnd(Runnable)`             | END   | Alias of `thenWaitUntil`                      |
| `thenWaitUntil(maxTicks, Runnable)`        | END   | Retry assertion failures for at most `maxTicks` |
| `thenWaitUntilAtStart(maxTicks, Runnable)` | START | Same, before world tick                       |
| `thenWaitUntilAtEnd(maxTicks, Runnable)`   | END   | Alias of bounded `thenWaitUntil`              |
| `thenSucceed()`                            | END   | Pass the test                                 |
| `thenFail(msg)`                            | END   | Fail the test                                 |

## Tick phases

Every server tick has a START phase and an END phase. World logic (tile entity updates, hopper transfers, machine processing) runs between them.

Default sequence methods run at END, so assertions see the world after it has ticked. Use the `AtStart` variants to deliver input before the world ticks, which is what you want when testing machines that consume their input during the tick.

```mermaid
sequenceDiagram
    accTitle: START, world, and END phase order
    accDescr: The test registers work before tick one. Each tick runs START actions, world logic, END actions, and then the timeout check.
    participant Body as Test body
    participant Start as START phase
    participant World as Minecraft world logic
    participant End as END phase

    Body->>Start: Register callbacks and sequence steps before tick 1
    loop Each counted test tick
        Start->>Start: Advance clock, delayed actions, START steps
        Start->>World: Continue the server tick
        World->>End: Blocks and machines expose updated state
        End->>End: onEachTick, succeedWhen, END steps
        End->>End: Check timeout last
    end
```

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
    .thenExecuteAtStart(stimulus);  // throws: START cannot follow END in the same tick
```

Use `thenIdle(1)` to push the START event to the next tick:

```java
helper.startSequence()
    .thenExecute(assertion)
    .thenIdle(1)
    .thenExecuteAtStart(nextStimulus);
```

## Setup code and `startSequence`

The test body runs immediately when the test starts, before the first counted tick. A leading `thenExecuteAtStart` runs at START of tick 1, and a leading `thenExecute` runs at END of tick 1. Structural setup usually belongs before `startSequence()` so the sequence can focus on stimulus and assertions:

```java
@GameTest(template = "stone_platform")
public static void example(GameTestHelper helper) {
    helper.setBlock(0, 1, 0, Blocks.chest);
    helper.startSequence()
        .thenWaitUntil(() -> helper.assertBlockPresent(Blocks.chest, 0, 1, 0))
        .thenSucceed();
}
```

## `thenIdle` counts ticks, not gaps

Ticks are author-facing and 1-based. `thenIdle(1)` advances one counted tick from the current sequence point; between two events, that means the next event lands on the next tick.

END-phase sequence actions scheduled on the timeout boundary are evaluated before the test is marked timed out. For example, `thenIdle(40).thenSucceed()` passes at END of tick 40 with `timeoutTicks = 40`.

START-phase actions after an initial idle run before the next world tick after that many ticks have elapsed. For example, `thenIdle(40).thenExecuteAtStart(...)` targets START of tick 41, so it is outside a `timeoutTicks = 40` window.

## Assertions inside thenExecute

Throw any `AssertionError` subclass. It propagates through the sequence and fails the test with the usual position context.

## Prefer state polling over fixed delays

```java
.thenWaitUntil(500, () -> gtnh.assertMachineFormed(controller))
```

is more reliable than `thenIdle(fixedRecipeLength)`. See [Design principle 4](../contributing/principles.md).

Wait steps retry only `AssertionError`, which includes Horizon-QA assertion failures. A runtime exception or another error fails the test immediately instead of being treated as a condition that may become true later.

Bounded waits attempt their condition once per matching phase, including both their first and final scheduled ticks. If the final attempt still throws an assertion, the test fails immediately with the step number, declaration callsite, attempt count, and latest assertion message. When the condition succeeds, the sequence continues immediately at the next legal phase. Add an explicit `thenIdle(...)` when a test intentionally needs fixed spacing or a minimum observation period; an idle following a wait is measured from the tick when that wait actually completes.

Optional labels make failure output easier to scan:

```java
helper.startSequence()
    .thenWaitUntil("network becomes active", 100, () -> assertNetworkActive(controller))
    .thenExecute("install encoded pattern", () -> installPattern(pattern))
    .thenWaitUntil("crafted output reaches storage", 260, () -> assertStored(output))
    .thenSucceed();
```

Unlabeled steps are still identified automatically by their sequence index and source file line. Sequence state is
also available through `getSteps()` and `getActiveStep()` for investigation tooling.

## Interaction with warp

`runRecipe` and `fastForwardTicks` advance the event recorder clock, not the sequence scheduler. The two clocks are independent. When reading timing logs, check which clock an event came from before drawing conclusions.

## Choosing the right tool

|                    | Use when                                                   |
|--------------------|------------------------------------------------------------|
| `onEachTick`       | Something must hold true on every tick until the test ends |
| `GameTestSequence` | Steps need to happen in order with delays between them     |
| `succeedWhen`      | Waiting for a single condition to become true              |
