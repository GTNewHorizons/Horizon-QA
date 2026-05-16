---
title: Sequences & timing
description: GameTestSequence for ordered steps, delays, and bounded waits without real-time sleeps.
tags:
  - guides
  - timing
---

# Sequences & timing

`GameTestSequence` schedules actions on future **test ticks**. Use it for multi-step setup without resorting to real-time sleeps.

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

| Method                                | Effect                                                            |
|---------------------------------------|-------------------------------------------------------------------|
| `thenIdle(n)`                         | Advance the schedule by `n` ticks (no-op)                         |
| `thenExecute(Runnable)`               | Run once at the scheduled tick                                    |
| `thenExecuteFor(n, Runnable)`         | Run every tick for `n` ticks                                      |
| `thenWaitUntil(Runnable)`             | Poll each tick until the runnable returns without throwing        |
| `thenWaitUntil(maxTicks, Runnable)`   | Same, but caps the wait by advancing the schedule by `maxTicks`   |
| `thenSucceed()`                       | Pass at the scheduled tick                                        |
| `thenFail(msg)`                       | Fail at the scheduled tick                                        |

!!! info "One sequence per test"

    `startSequence()` may only be called once per test. For an immediate pass that needs no delay, use `helper.succeed()` rather than building a sequence that ends with `thenSucceed()`.

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
