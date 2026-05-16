---
title: Your first test
description: Write, run, and understand a minimal @GameTest method end-to-end.
tags:
  - getting-started
---

# Your first test

A valid test is a **public static** method with exactly one parameter of type `GameTestHelper`, annotated with `@GameTest`, inside a class annotated with `@GameTestHolder`.

## Minimal passing test

```java
@GameTestHolder("mymod") // (1)!
public class SmokeTests {

    @GameTest(timeoutTicks = 20) // (2)!
    public static void emptyCellPasses(GameTestHelper helper) {
        helper.succeed(); // (3)!
    }
}
```

1.  `value = "mymod"` becomes the namespace for test ids (`mymod:SmokeTests.emptyCellPasses`) and for unqualified `template` lookups.
2.  Hard timeout. The runner fails the test if it has not finished by tick 20.
3.  Marks the test passed immediately. Use this when every assertion is done in the first tick.

Omitting `template` yields an empty void cell — useful for pure API smoke tests.

## Test with a structure

```java
@GameTest(template = "platform", timeoutTicks = 40)
public static void blockStillThere(GameTestHelper helper) {
    helper.assertBlockPresent(helper.absolute(0, 0, 0), Blocks.stone);
    helper.succeed();
}
```

Template `platform` resolves to `mymod:platform`, which the loader reads from:

```text
assets/mymod/gameteststructures/platform.json
assets/mymod/gameteststructures/platform_tiles.nbt   (optional)
```

## Success patterns

Pick exactly one. Mixing them is almost always a bug.

| Pattern                                       | When to use                                                       |
|-----------------------------------------------|-------------------------------------------------------------------|
| `helper.succeed()`                            | All assertions done in one tick                                   |
| `helper.succeedWhen(() -> condition)`         | Pass on the first tick where `condition` is true                  |
| `helper.succeedAtTimeout()`                   | **Negative tests** — pass only if nothing failed before timeout   |
| `helper.startSequence()…thenSucceed()`        | Multi-step timed flows (see [Sequences & timing](../guide/sequences.md)) |

!!! warning "One success path per test"

    Do not call `succeed()` and `startSequence()` together unless you understand the sequence API. `startSequence()` may only be called **once** per test.

## Assertions

Common helpers on `GameTestHelper`:

`assertTrue` / `assertFalse`
:   Boolean predicate with a failure message.

`assertEquals` / `assertNotEquals`
:   Standard equality assertions with `expected` / `actual` formatting.

`assertBlockPresent` / `assertBlockAbsent`
:   Block-level assertions at an absolute position (use `helper.absolute(x, y, z)`).

`fail(String)`
:   Immediate failure — throws `GameTestAssertException`.

All failures include the test origin so the visual overlay can highlight the offending cell.

## GTNH entry point

```java
GTNHGameTestHelper gtnh = helper.gtnh();
Multiblock ebf = gtnh.multiblock(at(1, 0, 0)); // controller, test-relative
```

Use **test-relative** positions via `TestPos.at(x, y, z)` or `helper.absolute(...)`. Hardcoded world coordinates couple a test to a single placement and break the moment the grid layout shifts. See [GTNH multiblock API](../guide/gtnh-api.md).

## Next steps

- [Writing tests](../guide/writing-tests.md) — batches, `required`, rotations, cleanup.
- [Negative assertions](../guide/negative-tests.md) — `onEachTick` patterns.
- [Annotations](../reference/annotations.md) — full `@GameTest` attribute list.
