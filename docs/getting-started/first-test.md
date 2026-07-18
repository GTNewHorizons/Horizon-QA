---
title: Your first test
description: Write, run, and understand a minimal @GameTest method end-to-end.
---

# Your first test

A valid test is a **public static** method with exactly one parameter of type `GameTestHelper`, annotated with `@GameTest`, inside a class annotated with `@GameTestHolder`.

## Minimal passing test

```java
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;

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
3.  Marks the test passed immediately. Use this when every assertion finishes synchronously in the test body.

Omitting `template` yields an empty test cell, useful for API smoke tests and small fixtures built with `setBlock`.

## Run it

Start the server in interactive mode, then run the generated test ID:

```text
/horizonqa run mymod:SmokeTests.emptyCellPasses
```

The server log should include:

```text
PASSED   mymod:SmokeTests.emptyCellPasses
```

If the test is not discovered, confirm that the holder is on the server runtime classpath and that the method signature is exactly `public static void name(GameTestHelper helper)`.

## Test with a structure

```java
@GameTest(template = "single_stone", timeoutTicks = 40)
public static void blockStillThere(GameTestHelper helper) {
    helper.assertBlockPresent(Blocks.stone, helper.pos("stone"));
    helper.succeed();
}
```

Template `single_stone` resolves to `mymod:single_stone`, which the loader reads from:

```text
assets/mymod/horizonqastructures/single_stone.json
assets/mymod/horizonqastructures/single_stone.snbt   (optional; text structure data)
assets/mymod/horizonqastructures/single_stone.nbt    (optional fallback; binary structure data)
```

The `stone` label is stored in the template JSON under `annotations.labels`. Labels keep the Java test readable and are automatically rotated with the structure.

## Success patterns

Pick exactly one. Mixing them is almost always a bug.

| Pattern                                       | When to use                                                       |
|-----------------------------------------------|-------------------------------------------------------------------|
| `helper.succeed()`                            | All assertions done in one tick                                   |
| `helper.succeedWhen(() -> condition)`         | Pass on the first tick where `condition` is true                  |
| `helper.succeedAtTimeout()`                   | **Negative tests**: pass only if nothing failed before timeout    |
| `helper.startSequence().thenSucceed()`        | Multi-step timed flows (see [Sequences and timing](../guide/sequences.md)) |

!!! warning "One success path per test"

    Use one sequence per test and make its completion behavior explicit. Calling `succeed()` immediately after creating a sequence ends the test before the scheduled steps can run.

## Assertions

Common helpers on `GameTestHelper`:

`assertTrue` / `assertFalse`
:   Boolean predicate with a failure message.

`assertEquals` / `assertNotEquals`
:   Standard equality assertions with `expected` / `actual` formatting.

`assertBlockPresent` / `assertBlockAbsent`
:   Block-level assertions at a **test-local** position. Pass raw local coordinates or a label from `helper.pos("name")`.

`fail(String)`
:   Immediate failure; throws `GameTestAssertException`.

Framework assertion helpers attach the test origin or a more specific block position so the visual overlay can highlight the relevant cell.

## GTNH entry point

```java
GTNHGameTestHelper gtnh = helper.gtnh();
Multiblock ebf = gtnh.multiblock(helper.pos("controller"));
```

Use named labels from the exported template for load-bearing coordinates such as controllers and hatches. Fall back to **test-relative** positions via `TestPos.at(x, y, z)` only for throwaway coordinates created inside the test. Hardcoded world coordinates couple a test to a single placement and break the moment the grid layout shifts. See [GTNH multiblock API](../guide/gtnh-api.md).

!!! warning "Keep coordinate spaces separate"

    Most Horizon-QA helpers expect test-local positions and convert them to world coordinates internally. Use `helper.absolute(...)` only for direct world or mod APIs that explicitly require an absolute position.

## Next steps

- [Writing tests](../guide/writing-tests.md) for batches, `required`, rotations, cleanup.
- [Negative assertions](../guide/negative-tests.md) for `onEachTick` patterns.
- [Annotations](../reference/annotations.md) for the full `@GameTest` attribute list.
