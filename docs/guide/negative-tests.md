---
title: Negative assertions
description: Assert that a bad state never occurs over a tick window — the framework's primary idiom.
tags:
  - guides
  - negative
---

# Negative assertions

Horizon-QA is aimed at tests that assert a bad state **never** occurs over a tick window. That category covers most of the regressions a traditional unit test will quietly miss.

## Core pattern

```java
@GameTest(template = "ebf_no_coils", timeoutTicks = 60)
public static void doesNotFormWithoutCoils(GameTestHelper helper) {
    Multiblock ebf = helper.gtnh().multiblock(at(1, 0, 0));
    ebf.assertNeverForms("EBF formed without coils");
}
```

For GT multiblock formation tests, `assertNeverForms(...)` forces a structure check immediately, registers a per-tick
negative invariant, and succeeds at timeout. That forced first check matters when you mutate an exported valid template:
the controller may otherwise still hold a stale `mMachine=true` value from placement.

The equivalent generic pattern is:

```java
helper.onEachTick(() -> helper.assertFalse(ebf.isFormed(), "EBF formed without coils"));
helper.succeedAtTimeout();
```

| Call                                              | Role                                                                  |
|---------------------------------------------------|-----------------------------------------------------------------------|
| `onEachTick(Runnable)`                            | Runs the callback every test tick until the test passes or fails      |
| `assertFalse(ebf.isFormed(), …)`                  | Fails immediately on the tick where the machine forms                 |
| `succeedAtTimeout()`                              | Passes at the END of the final allowed tick if nothing failed         |

The final allowed tick is still observed before `succeedAtTimeout()` passes.

A transient formation fails on **that tick**, not at the end of the window — the framework does not need to wait out the full timeout to report the failure.

## When to use `succeedWhen` instead

Use `succeedWhen(() -> condition)` when you wait for a **positive** eventual state:

```java
helper.succeedWhen(() -> helper.getBlockState(pos).getBlock() == Blocks.redstone_block);
```

!!! warning "`succeedWhen` is not the right tool for invariants"

    `succeedWhen` exits the moment its predicate becomes true. An invariant that should hold **every** tick wants `onEachTick` + `succeedAtTimeout`, not `succeedWhen`.

## Continuous invariants

Combine multiple checks in a single callback so they share the same window:

```java
helper.onEachTick(() -> {
    helper.assertFalse(ebf.isFormed(), "formed");
    helper.assertFalse(ebf.isActive(), "started recipe");
});
helper.succeedAtTimeout();
```

## Sequences vs. polling

For staged scenarios — "insert items, then assert no recipe for 40 ticks, then supply EU" — prefer [Sequences & timing](sequences.md) over manual tick counters inside `onEachTick`. Sequences handle the scheduling; tick counters never quite do.

## Design alignment

Negative tests implement [Design principle 5 — Negative tests are load-bearing](../contributing/principles.md). Also see [Principle 4 — Wait on state, not ticks](../contributing/principles.md): tick counts on `@GameTest` are **timeouts**, never recipe-duration proxies.
