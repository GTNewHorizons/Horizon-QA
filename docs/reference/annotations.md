---
title: Annotations
description: Reference for @GameTest, @GameTestHolder, batch hooks, and stability markers.
---

# Annotations

## `@GameTest`

Marks a public static test method with signature `void name(GameTestHelper helper)`.

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

Stability: `@Experimental` (entire public API is experimental in 0.x.x).

## `@GameTestHolder`

Marks a class containing one or more `@GameTest` methods.

| Attribute         | Type     | Default      | Description                                                          |
|-------------------|----------|--------------|----------------------------------------------------------------------|
| `value`           | `String` | *(required)* | Namespace for test ids and template lookups (typically the mod id)   |
| `templatePrefix`  | `String` | `""`         | Prepended to relative template paths declared on `@GameTest`         |

Stability: `@Experimental`.

Holder `value` must match `[a-z0-9_.-]+`. `templatePrefix` cannot begin or end with `/`, contain `//`, or contain the substring `..`.

## `@BeforeBatch` and `@AfterBatch`

Public static void no-arg methods bound to one global batch name.

| Attribute | Type     | Description                                            |
|-----------|----------|--------------------------------------------------------|
| `value`   | `String` | Batch name; must match `GameTest.batch()` to bind      |

During automatic or manually reported execution, every matching `@BeforeBatch` hook runs once before tests in the batch start. Every matching `@AfterBatch` hook runs once after all of those tests finish. A failed before-hook blocks the batch; a failed after-hook is reported as an infrastructure error.

Normal interactive commands do not group by `batch` and do not invoke batch hooks.

Use `@BeforeBatch("")` and `@AfterBatch("")` for the default batch. Named batches use the same validation rules as `GameTest.batch()`. Because names are not holder-namespaced, prefer a mod-prefixed value when collisions are possible.

Stability: `@Experimental`.

## `@Stable` and `@Experimental`

API stability markers on public framework types. See [Versioning](versioning.md) for what each annotation commits to across releases.

`@Experimental`
:   May change without a major version bump. **All mod-facing API is `@Experimental` in 0.x.x**, including `GameTestHelper`, `TestPos`, and the test annotations, even where signatures still expose internal types or other experimental helpers.

`@Stable`
:   Reserved for 1.0.0 onward: types whose public signatures no longer leak internal or experimental types, and whose contracts are committed across minor versions.

Expect breaking API refinements in 0.x.x; pin versions and budget for updates until the first `@Stable` graduation in 1.0.0. The deprecation cycle that applies from 1.0.0 onward is described in [Versioning, deprecation policy](versioning.md#deprecation-policy).

## Test ID format

```text
<holder.value>:<ClassSimpleName>.<methodName>
```

Used in commands, JUnit XML (`classname` / `name`), batch summaries, selectors, and logs.

Discovery constructs this ID only after the holder and method pass validation; duplicate IDs are excluded from the runnable set.
