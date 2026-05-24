---
title: Sequences & timing
description: GameTestSequence for ordered steps, delays, and bounded waits without real-time sleeps.
tags:
  - guides
  - timing
---

# Sequences & timing

`GameTestSequence` schedules actions on future **test ticks**. Use it for multi-step setup without resorting to real-time sleeps.

## Test tick phases

Each server tick has two phases:

- **START** — fires before world logic (tile entity ticks, hopper transfers, machine processing, etc.)
- **END** — fires after world logic

Default sequence methods (`thenExecute`, `thenWaitUntil`, `thenSucceed`) all run at **END**. This means assertions observe the world state after the current tick's processing has completed — the correct point for checking machine outputs, inventory changes, or other side effects.

Use the explicit `AtStart` variants when a sequence step must deliver stimulus _before_ the world tick in which you want it to take effect.

## Basic sequence

```java
@GameTest(template = "stone_platform", timeoutTicks = 60)
public static void delayedAssert(GameTestHelper helper) {
    helper.startSequence()
        .thenIdle(10)
        .thenExecute(() -> helper.assertBlockPresent(helper.absolute(0, 0, 0), Blocks.stone))
        .thenSucceed();
}
```

| Method                                     | Phase | Effect                                                             |
|--------------------------------------------|-------|--------------------------------------------------------------------|
| `thenIdle(n)`                              | —     | Advance the schedule by `n` ticks (no-op)                          |
| `thenExecute(Runnable)`                    | END   | Run once after world tick at the scheduled tick                    |
| `thenExecuteAtStart(Runnable)`             | START | Run once before world tick at the scheduled tick                   |
| `thenExecuteAtEnd(Runnable)`               | END   | Alias for `thenExecute`                                            |
| `thenExecuteFor(n, Runnable)`              | END   | Run every tick for `n` ticks (after world tick)                    |
| `thenExecuteForAtStart(n, Runnable)`       | START | Run every tick for `n` ticks (before world tick)                   |
| `thenExecuteForAtEnd(n, Runnable)`         | END   | Alias for `thenExecuteFor`                                         |
| `thenWaitUntil(Runnable)`                  | END   | Poll after world tick until the runnable returns without throwing  |
| `thenWaitUntilAtStart(Runnable)`           | START | Poll before world tick until the runnable returns without throwing |
| `thenWaitUntil(maxTicks, Runnable)`        | END   | Same, but advances the schedule by `maxTicks` when it passes       |
| `thenWaitUntilAtStart(maxTicks, Runnable)` | START | Same, poll before world tick                                       |
| `thenSucceed()`                            | END   | Pass at the scheduled tick (after world tick)                      |
| `thenFail(msg)`                            | END   | Fail at the scheduled tick (after world tick)                      |

!!! info "One sequence per test"

    `startSequence()` may only be called once per test. For an immediate pass that needs no delay, use `helper.succeed()` rather than building a sequence that ends with `thenSucceed()`.

## thenIdle semantics

`thenIdle(N)` advances the schedule by exactly `N` test ticks. A value of `1` creates a one-tick boundary between surrounding steps: both the preceding and the following events will not run in the same tick.

!!! note "Off-by-one change from pre-1.x"

    In versions before this phase model was introduced, `thenIdle(N)` created an effective gap of `N-1` ticks because sequence events were polled at the start of the server tick rather than using zero-based sequence ticks. All `thenIdle(N)` calls now wait exactly `N` ticks. Tests that relied on the old counting will run one tick longer than before, but this rarely matters in practice because most tests use generous timeout values.

## Phase ordering constraint

Within a single scheduled tick, START events must come before END events. The builder enforces this:

```java
// Valid: START then END at the same tick
helper.startSequence()
    .thenExecuteAtStart(stimulus)
    .thenExecute(assertion);   // END, same tick — allowed

// Invalid: END then START at the same tick — throws IllegalStateException
helper.startSequence()
    .thenExecute(assertion)
    .thenExecuteAtStart(stimulus);  // cannot go back to START
```

To schedule a START event after an END event, advance to the next tick first:

```java
helper.startSequence()
    .thenExecute(assertion)
    .thenIdle(1)
    .thenExecuteAtStart(nextStimulus);
```

## Machine interaction example

For machines that process items during world tick (AE2 I/O Ports, hoppers, GT recipes), provide input at START and observe output at END:

```java
helper.startSequence()
    .thenExecuteAtStart(() -> ioport.setInventorySlotContents(0, cell.copy()))
    .thenExecute(() -> {
        helper.assertNull(ioport.getStackInSlot(0));
        helper.assertTrue(ItemStack.areItemStacksEqual(ioport.getStackInSlot(6), cell));
    })
    .thenSucceed();
```

## Assertions inside `thenExecute`

Failures throw `GameTestAssertException`, which propagates through the sequence runner and fails the test with positional context — no extra wiring required.

## Waiting on machine state

Prefer GTNH helpers that poll **machine state** inside `thenWaitUntil`:

```java
.thenWaitUntil(500, () -> gtnh.assertMachineFormed(controller))
```

rather than `thenIdle(fixedRecipeLength)`. See [Design principle 4 — Wait on state, not ticks](../contributing/principles.md).

## Interaction with warp

Warped ticks advance the **event recorder clock** inside `runRecipe` / `fastForwardTicks`. Sequence scheduling uses the **outer test tick** of the runner.

Mixing the two in a single test is supported and occasionally necessary, but the two clocks make timing logs harder to read. When debugging, identify which clock each event came from before drawing conclusions.

## Choosing the right primitive

| Mechanism            | Best for                                                       |
|----------------------|----------------------------------------------------------------|
| `onEachTick`         | An invariant that must hold **every** tick until timeout       |
| `GameTestSequence`   | Ordered steps, delayed actions, bounded waits                  |
| `succeedWhen`        | A single predicate that becomes true once                      |
