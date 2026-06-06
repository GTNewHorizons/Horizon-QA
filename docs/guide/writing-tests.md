---
title: Writing tests
description: Class and method shape, batches, rotations, cleanup, and the two GT API styles.
tags:
  - guides
  - authoring
---

# Writing tests

## Class and method shape

```java
@GameTestHolder("mymod")
public class AssemblerTests {

    @GameTest(template = "assembler_line", timeoutTicks = 2000, batch = "assembler")
    public static void processesOneRecipe(GameTestHelper helper) {
        // ...
        helper.succeed();
    }
}
```

Rules enforced at discovery — invalid methods are skipped with a log warning, not a crash:

- Method must be **`public static`**.
- Exactly one parameter: **`GameTestHelper`**.
- Return type **`void`**.

## Test identity

Every test receives a stable id:

```text
<@GameTestHolder value>:<ClassSimpleName>.<methodName>
```

Example: `mymod:AssemblerTests.processesOneRecipe`. Use this id with `/horizonqa run` and as the `classname` / `name` pair in JUnit XML.

## Template attribute

| Form                              | Resolves to                                                             |
|-----------------------------------|-------------------------------------------------------------------------|
| `template = ""`                   | Empty void cell (no structure placement)                                |
| `template = "ebf"`                | `<holder>:ebf`, or `<holder>:<prefix>/ebf` when `templatePrefix` is set |
| `template = "other:path/to/cell"` | Used verbatim as a fully qualified `namespace:path`                     |

## Batches

Tests sharing the same `batch = "name"` are placed in one grid sweep. Hook setup and teardown with batch-scoped lifecycle methods:

```java
@BeforeBatch("assembler")
public static void warmCaches() { /* no args */ }

@AfterBatch("assembler")
public static void tearDown() { /* no args */ }
```

Batch methods must be **public static void** and take **no parameters**. They run on the server thread before/after every test in that batch.

## `required = false`

Tests marked `required = false` may fail or time out without failing the overall run. CI still reports them in JUnit XML and status JSON; see [CI & JUnit reports](ci.md#optional-tests) for the exact reporting semantics.

!!! danger "Do not use `required = false` as a permanent mute"

    A test that has been failing-as-optional for months is a test nobody reads. Either fix it, gate it on the relevant condition, or delete it.

## Rotation

`rotation` on `@GameTest` is `0–3`: none, 90°, 180°, 270° clockwise around Y, matching structure placement conventions. Setting it to a non-zero value is the cheapest way to catch templates that quietly hardcoded a facing.

If a test only passes at `rotation = 0`, document the reason in the method body — usually it indicates a coordinate that should have been a role-based lookup.

## Cleanup and isolation

Follow [Design principle 6 — Leave no trace](../contributing/principles.md):

- Call `gtnh.withTestRecipe(...)` for synthetic recipes; cleanup runs automatically at end of test via `afterTest`.
- Register `helper.afterTest(() -> { ... })` for any manual registry or world mutation outside framework helpers.
- Do not leave items, fluids, or fake players attached when the test cell is cleared.

Isolation violations emit `IsolationViolation` events and fail the test. See [Test event log](../reference/events.md).

## Assertions and failure messages

Failure messages are read by humans in CI long before anyone reaches for the in-game overlay. Write messages that answer **what was expected vs. what was observed**.

```java
helper.assertEquals(64, actualCount,
    "Output bus should contain 64 copper plates after recipe");
```

Avoid messages like `"wrong count"` or `"assertion failed"` — they force the reader to open the JUnit XML to learn anything.

## Imperative vs fluent GT API

Two styles are supported. Both compile to the same calls; pick by what is most legible for the test.

=== "Fluent (`Multiblock`)"

    ```java
    Multiblock ebf = helper.gtnh().multiblock(at(1, 0, 0));
    ebf.assertFormed();
    ebf.inputBus(0).insert(...).programmedCircuit(0);
    ebf.energyHatch(0).supply(TierEU.EV, 1, 900);
    ebf.runRecipe();
    ebf.outputs().assertContains(...);
    ```

=== "Imperative (`GTNHGameTestHelper`)"

    ```java
    gtnh.assertMachineFormed(controller);
    gtnh.supplyEU(energyHatch, TierEU.EV, 1, 900);
    gtnh.runUntilMachineIdle(controller, 1500);
    gtnh.assertItemInBus(outputBus, stack);
    ```

Prefer **role-based** hatch indices (`inputBus(0)`) over raw coordinates when using `Multiblock`. Coordinates belong in the template — not throughout the test method.

## Further reading

- [Negative assertions](negative-tests.md)
- [Sequences & timing](sequences.md)
- [Annotations](../reference/annotations.md)
