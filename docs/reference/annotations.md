---
title: Annotations
description: Reference for @GameTest, @GameTestHolder, batch hooks, and stability markers.
tags:
  - reference
  - annotations
---

# Annotations

## `@GameTest`

Marks a public static test method with signature `void name(GameTestHelper helper)`.

| Attribute       | Type      | Default | Description                                                              |
|-----------------|-----------|---------|--------------------------------------------------------------------------|
| `template`      | `String`  | `""`    | Structure name; see [Structure templates](../guide/structures.md)        |
| `timeoutTicks`  | `int`     | `100`   | Full server ticks the test may observe before timing out                 |
| `batch`         | `String`  | `""`    | Batch group name for ordering and `@BeforeBatch` / `@AfterBatch` hooks   |
| `required`      | `boolean` | `true`  | If `false`, a failure may not fail the overall run                       |
| `rotation`      | `int`     | `0`     | Structure rotation `0..3` (90-degree steps clockwise around Y)           |

`timeoutTicks = N` allows the test to observe ticks `1..N`. Timeout is reported after the END phase of tick `N`. END-phase sequence actions scheduled on that boundary run before timeout is reported, so a sequence can still pass at `timeoutTicks`.

Stability: `@Experimental` (entire public API is experimental in 0.x.x).

## `@GameTestHolder`

Marks a class containing one or more `@GameTest` methods.

| Attribute         | Type     | Default      | Description                                                          |
|-------------------|----------|--------------|----------------------------------------------------------------------|
| `value`           | `String` | *(required)* | Namespace for test ids and template lookups (typically the mod id)   |
| `templatePrefix`  | `String` | `""`         | Prepended to relative template paths declared on `@GameTest`         |

Stability: `@Experimental`.

## `@BeforeBatch` / `@AfterBatch`

Public static void no-arg methods that run once before/after every test sharing a `batch` value on `@GameTest`.

| Attribute | Type     | Description                                            |
|-----------|----------|--------------------------------------------------------|
| `value`   | `String` | Batch name; must match `GameTest.batch()` to bind      |

Stability: `@Experimental`.

## `@Stable` / `@Experimental`

API stability markers on public framework types. See [Versioning](versioning.md) for what each annotation commits to across releases.

`@Experimental`
:   May change without a major version bump. **All mod-facing API is `@Experimental` in 0.x.x**, including `GameTestHelper`, `TestPos`, and the test annotations, even where signatures still expose internal types or other experimental helpers.

`@Stable`
:   Reserved for 1.0.0 onward: types whose public signatures no longer leak internal or experimental types, and whose contracts are committed across minor versions.

Expect breaking API refinements in 0.x.x; pin versions and budget for updates until the first `@Stable` graduation in 1.0.0. The deprecation cycle that applies from 1.0.0 onward is described in [Versioning -- Deprecation policy](versioning.md#deprecation-policy).

## Test id format

```text
<holder.value>:<ClassSimpleName>.<methodName>
```

Used in commands, JUnit XML (`classname` / `name`), batch summaries, selectors, and logs.
