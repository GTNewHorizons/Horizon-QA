---
title: Annotations
description: Reference for @GameTest, @GameTestHolder, parameter sources, and batch hooks.
---

# Annotations

## `@GameTest`

Marks a public static test method with signature `void name(GameTestHelper helper)`. With `@MethodSource`, supplied
parameters follow `GameTestHelper`; see [Parameterized tests](../guide/parameterized-tests.md).

| Attribute       | Type      | Default | Description                                                            |
|-----------------|-----------|---------|------------------------------------------------------------------------|
| `template`      | `String`  | `""`    | Structure name; see [Structure templates](../guide/structures.md)      |
| `timeoutTicks`  | `int`     | `100`   | Full server ticks the test may observe before timing out               |
| `batch`         | `String`  | `""`    | Reported-run group name for ordering and `@BeforeBatch` / `@AfterBatch` hooks |
| `required`      | `boolean` | `true`  | If `false`, a failure may not fail the overall run                     |
| `rotation`      | `int`     | `0`     | Structure rotation `0-3` (90° steps clockwise around Y)                |

`timeoutTicks = N` allows the test to observe ticks `1..N`. Timeout is reported after the END phase of tick `N`. END-phase sequence actions scheduled on that boundary run before timeout is reported, so a sequence can still pass at `timeoutTicks`.

Validation rules:

- `timeoutTicks` must be greater than `0`.
- `rotation` must be between `0` and `3`.
- `batch` must be empty or match `[A-Za-z0-9_.-]+`.
- The literal batch name `default` is reserved. Use `""` for the default batch.

## `@MethodSource`

Expands one `@GameTest` method into independently selectable and reported cases. `value` names a public static
no-argument source method in the same holder; the default `""` uses the test method name. Sources execute during
discovery and enumerate cases in encounter order; they do not define an automatic Cartesian product.

Sources may return a `Stream`, `Iterable`, `Iterator`, or array. Every element must be a `GameTestArguments` row, named
with `GameTestArguments.named(name, firstValue, remainingValues...)` or assigned an encounter index with
`GameTestArguments.of(firstValue, remainingValues...)`. Use `namedValues(name, Object[])` or `ofValues(Object[])` when a
later argument is `null` or an array. A source may contain at most 256 rows. Case names must be at most 128 characters,
match `[A-Za-z0-9_.-]+`, and be unique within the source.

## `@GameTestHolder`

Marks a class containing one or more `@GameTest` methods.

| Attribute         | Type       | Default      | Description                                                        |
|-------------------|------------|--------------|--------------------------------------------------------------------|
| `value`           | `String`   | *(required)* | Namespace for test ids and template lookups (typically the mod id) |
| `templatePrefix`  | `String`   | `""`         | Prepended to relative template paths declared on `@GameTest`       |
| `requiredMods`    | `String[]` | `{}`         | Mod ids that must be loaded before this holder is inspected        |

Holder `value` must match `[a-z0-9_.-]+`. `templatePrefix` cannot begin or end with `/`, contain `//`, or contain the substring `..`.

When any `requiredMods` entry is absent, discovery reads the holder and its `@GameTest` methods from Forge ASM metadata without loading the holder class. Each selected method is reported as `skipped` with type `MISSING_REQUIRED_MOD`; its method body, template, and batch hooks do not run. This makes a separate compatibility holder safe even when its bytecode directly references optional-mod classes:

```java
@GameTestHolder(value = "appliedenergistics2", requiredMods = "ae2fc")
public class FluidCraftingCompatibilityTests {
    // @GameTest methods may reference AE2 Fluid Crafting classes directly.
}
```

Put only tests with the same dependency set in a mod-gated holder. Use a separate holder when one compatibility area needs an optional mod and other tests do not.

## `@BeforeBatch` and `@AfterBatch`

Public static void no-arg methods bound to one global batch name.

| Attribute | Type     | Description                                            |
|-----------|----------|--------------------------------------------------------|
| `value`   | `String` | Batch name; must match `GameTest.batch()` to bind      |

During automatic or manually reported execution, every matching `@BeforeBatch` hook runs once before tests in the batch
start. Once batch setup begins, every matching `@AfterBatch` hook is owed exactly once. After-hooks run after normal test
completion and are still attempted after a before-hook failure, setup failure, infrastructure failure, or reported-run
shutdown. A failed before-hook blocks the batch; a failed after-hook is reported as an infrastructure error without
preventing later after-hooks from running.

Normal interactive commands do not group by `batch` and do not invoke batch hooks.

Use `@BeforeBatch("")` and `@AfterBatch("")` for the default batch. Named batches use the same validation rules as `GameTest.batch()`. Because names are not holder-namespaced, prefer a mod-prefixed value when collisions are possible.

## Test ID format

```text
<holder.value>:<ClassSimpleName>.<methodName>
```

Used in commands, JUnit XML (`classname` / `name`), batch summaries, selectors, and logs.

Discovery constructs this ID only after the holder and method pass validation; duplicate IDs are excluded from the runnable set.

Parameterized invocations append `[<caseName>]`, for example
`mymod:AssemblerTests.processesOneRecipe[distilled_water]`.
