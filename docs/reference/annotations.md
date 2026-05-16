---
title: Annotations
description: Reference for @GameTest, @GameTestHolder, batch hooks, and stability markers.
tags:
  - reference
  - annotations
---

# Annotations

## `@GameTest`

Marks a static test method with signature `void name(GameTestHelper helper)`.

| Attribute       | Type      | Default | Description                                                              |
|-----------------|-----------|---------|--------------------------------------------------------------------------|
| `template`      | `String`  | `""`    | Structure name; see [Structure templates](../guide/structures.md)        |
| `timeoutTicks`  | `int`     | `100`   | Maximum test duration in ticks (hard cap)                                |
| `batch`         | `String`  | `""`    | Batch group name for ordering and `@BeforeBatch` / `@AfterBatch` hooks   |
| `required`      | `boolean` | `true`  | If `false`, a failure may not fail the overall run                       |
| `rotation`      | `int`     | `0`     | Structure rotation `0–3` (90° steps clockwise around Y)                  |

Stability: `@Stable`.

## `@GameTestHolder`

Marks a class containing one or more `@GameTest` methods.

| Attribute         | Type     | Default      | Description                                                          |
|-------------------|----------|--------------|----------------------------------------------------------------------|
| `value`           | `String` | *(required)* | Namespace for test ids and template lookups (typically the mod id)   |
| `templatePrefix`  | `String` | `""`         | Prepended to relative template paths declared on `@GameTest`         |

Stability: `@Stable`.

## `@BeforeBatch` / `@AfterBatch`

Static no-arg methods that run once before/after every test sharing a `batch` value on `@GameTest`.

| Attribute | Type     | Description                                            |
|-----------|----------|--------------------------------------------------------|
| `value`   | `String` | Batch name — must match `GameTest.batch()` to bind     |

Stability: `@Stable`.

## `@Stable` / `@Experimental`

API stability markers on public framework types and members.

`@Stable`
:   Safe for mod authors to depend on. Source and binary compatibility are preserved across minor versions.

`@Experimental`
:   May change without a major version bump. Use behind feature flags or be prepared to update on framework upgrades.

Prefer `@Stable` entry points when writing tests against Horizon-QA.

## Test id format

```text
<holder.value>:<ClassSimpleName>.<methodName>
```

Used in commands, JUnit XML (`classname` / `name`), batch summaries, and logs.
