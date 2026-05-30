---
title: Examples mod
description: The examples/ Gradle subproject — runnable reference and regression coverage for the framework.
tags:
  - contributing
  - examples
---

# Examples mod

The `examples/` Gradle subproject demonstrates end-to-end usage against GT5-Unofficial and the usual GTNH runtime dependencies. It serves three purposes:

- **Runnable reference** for test authors.
- **Regression coverage** for the framework itself.
- **Source for documentation snippets** — the code on these pages is real.

It is **not** a template to copy wholesale into a gameplay mod. See [Package layout](../reference/package-layout.md).

## Layout

```text
examples/
  build.gradle.kts
  dependencies.gradle          # GT5-Unofficial, CoreMod, AE2, etc.
  src/main/java/.../examples/tests/
    BasicTests.java
    HelperApiTests.java
    StructureTests.java
    GTNHExampleTests.java
  src/main/resources/assets/horizonqaexamples/horizonqastructures/
```

Holder namespace: `horizonqaexamples` (`@GameTestHolder("horizonqaexamples")`).

## Running

```bash
./gradlew :examples:runServer
```

Interactive mode is the default, so no JVM flag is required for local example runs.

Then, in-game:

```text
/horizonqa runall horizonqaexamples
```

## What each test class covers

| Class                | Focus                                                              |
|----------------------|--------------------------------------------------------------------|
| `BasicTests`         | Pass / fail / timeout, optional `required = false`                 |
| `HelperApiTests`     | `GameTestHelper` blocks, items, fluids, sequences                  |
| `StructureTests`     | Template loading and block-level assertions                        |
| `GTNHExampleTests`   | EBF recipes, maintenance gating, EU supply, synthetic recipes, negative formation |

## Adding an example

When introducing a new **author-facing** feature:

1. Add a focused test method with a clear, behaviour-describing name.
2. Add or extend a structure template if needed.
3. Link the new method from the relevant page under `docs/guide/`.

Keep examples minimal — one concept per test method where you can.

## Dependency on the root project

`examples` uses `devOnlyNonPublishable(project(":"))`, which puts the in-development Horizon-QA jar on the classpath without publishing it to Maven from the examples build alone. That keeps the framework iteration loop fast: edit, recompile, run.
