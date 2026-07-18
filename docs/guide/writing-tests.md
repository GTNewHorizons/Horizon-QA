---
title: Writing tests
description: Class and method shape, batches, rotations, cleanup, and the two GT API styles.
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

Rules enforced at discovery (invalid methods are skipped with a log warning, not a crash):

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

## Template labels

Exported templates can carry named coordinate labels under `annotations.labels`. Prefer labels for every meaningful coordinate in a structure-backed test:

```java
TestPos controller = helper.pos("controller");
TestPos outputBus = helper.pos("output_bus");
Multiblock ebf = helper.gtnh().multiblock(controller);
```

`helper.pos("name")` returns test-relative coordinates with structure rotation applied. `helper.absolute("name")` returns the rotated world coordinate. If the label is missing, the test is reported as an infrastructure error with type `LABEL_ERROR`, because the fixture and Java code disagree.

## Batches

Batch grouping applies to automatic runs and manually started reported runs. The batch runner executes batch names one after another; tests sharing the same `batch = "name"` are placed together and tick concurrently. Hook setup and teardown with batch-scoped lifecycle methods:

```java
@BeforeBatch("assembler")
public static void warmCaches() { /* no args */ }

@AfterBatch("assembler")
public static void tearDown() { /* no args */ }
```

Batch methods must be **public static void** and take **no parameters**. Every matching `@BeforeBatch` method runs once before the reported batch starts. Every matching `@AfterBatch` method runs once after all tests in that batch finish.

Batch names are global across every discovered holder. Prefer a mod-prefixed name such as `mymod_assembler` when another mod could choose the same word.

!!! important "Interactive commands do not run batch hooks"

    Normal interactive `run`, `runall`, and `runfailed` commands launch selected tests directly. They ignore batch grouping and do not invoke `@BeforeBatch` or `@AfterBatch`. To exercise ordering and hooks locally, start the server with `-Dhorizonqa.mode=ci -Dhorizonqa.autoRun=false`, then launch a manually reported batch.

## `required = false`

Tests marked `required = false` may fail or time out without failing the overall run. CI still reports them in JUnit XML and status JSON; see [CI and JUnit reports](ci.md#optional-tests) for the exact reporting semantics.

!!! warning "Keep optional tests intentional"

    Use `required = false` for a time-limited quarantine, experimental coverage, or an environment-specific check. Keep the failure visible and remove the exemption when it should gate the build.

## Rotation

`rotation` on `@GameTest` is `0-3`: none, 90°, 180°, 270° clockwise around Y, matching structure placement conventions. Setting it to a non-zero value is the cheapest way to catch templates that quietly hardcoded a facing.

If a test only passes at `rotation = 0`, check for raw fixture coordinates, facings, or assumptions about GregTech list order. Document a fixed orientation only when it is an intentional constraint.

## Cleanup and isolation

Follow [Design principle 6, "Leave no trace"](../contributing/principles.md):

- Call `gtnh.withTestRecipe(...)` for synthetic recipes; cleanup runs automatically at end of test via `afterTest`.
- Register `helper.afterTest(() -> { ... })` for any manual registry or world mutation outside framework helpers.
- Do not leave items, fluids, or fake players attached when the test cell is cleared.

The built-in isolation scan fails when a GregTech tile entity leaks into the outer cell margin and warns about blocks outside a template footprint. It does not detect every kind of global or entity state. Register explicit cleanup for anything else your test mutates. See [Fixtures, coordinates, and isolation](../concepts/fixtures-and-isolation.md#cleanup-ownership).

## Assertions and failure messages

Failure messages are read by humans in CI long before anyone reaches for the in-game overlay. Write messages that answer **what was expected vs. what was observed**.

```java
helper.assertEquals(64, actualCount,
    "Output bus should contain 64 copper plates after recipe");
```

Avoid messages like `"wrong count"` or `"assertion failed"`; they force the reader to open the JUnit XML to learn anything.

## Imperative and fluent GT APIs

The fluent facade is the safer default for complete machine scenarios. The imperative helper exposes lower-level operations for setup and unusual controllers, but it does not provide an exact equivalent for every fluent operation.

=== "Fluent (`Multiblock`)"

    ```java
    Multiblock ebf = helper.gtnh().multiblock(helper.pos("controller"));
    ebf.assertFormed();
    ebf.inputBus(0).insert(...).programmedCircuit(0);
    ebf.energyHatch(0).supply(TierEU.EV, 1, 900);
    ebf.runRecipe();
    ebf.outputs().assertContains(ItemMatcher.of(...).count(expectedCount));
    ```

=== "Imperative setup (`GTNHGameTestHelper`)"

    ```java
    TestPos controller = helper.pos("controller");
    TestPos energyHatch = helper.pos("energy_hatch");
    TestPos inputHatch = helper.pos("input_hatch");

    gtnh.assertMachineFormed(controller);
    gtnh.fixAllMaintenanceIssues(controller);
    gtnh.supplyEU(energyHatch, TierEU.EV, 1, 900);
    gtnh.fillHatch(inputHatch, "nitrogen", 2000);
    ```

Prefer typed hatch and bus access (`inputBus(0)`, `energyHatch(0)`) over raw coordinates when using `Multiblock`. The index is the position in GregTech's live list for that hatch type, not a template label. Controllers with exotic or mod-specific hatch lists may require the imperative escape hatches.

`Bus.assertContains(ItemStack)` and `BusGroup.assertContains(ItemStack)` ignore stack size. Use `ItemMatcher.of(stack).count(n)` when quantity is part of the assertion.

## Further reading

- [Negative assertions](negative-tests.md)
- [Sequences and timing](sequences.md)
- [Annotations](../reference/annotations.md)
