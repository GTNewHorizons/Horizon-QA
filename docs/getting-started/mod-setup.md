---
title: Mod project setup
description: Wire Horizon-QA into a GTNH-style Gradle mod, lay out tests, and discover them at runtime.
tags:
  - getting-started
  - gradle
---

# Mod project setup

## Gradle dependency

Add Horizon-QA to your mod's `dependencies.gradle` (or equivalent). It belongs on the **dev / CI classpath only** — release jars of gameplay mods should not bundle it.

```groovy
dependencies {
    devOnlyNonPublishable('com.github.GTNewHorizons:Horizon-QA:<version>:dev')
    // runtimeOnlyNonPublishable(...) if tests execute in runServer for this mod
}
```

Pin the version to the same Horizon-QA build your pack or meta-repo uses. The `examples/` subproject in this repository shows a full GTNH dependency set (GT5-Unofficial, CoreMod, etc.) and is the canonical reference for the wiring.

## Runtime mode

Local server runs use interactive mode by default, so no JVM flag is required for `/horizonqa` commands, discovery, and visual debugging.

Automated server runs should use CI mode:

```text
-Dhorizonqa.mode=ci
```

=== "Gradle (Kotlin DSL)"

    ```kotlin
    tasks.named<JavaExec>("runServer") {
        jvmArgs("-Dhorizonqa.mode=ci")
    }
    ```

=== "Gradle (Groovy DSL)"

    ```groovy
    runServer {
        jvmArgs '-Dhorizonqa.mode=ci'
    }
    ```

Use `-Dhorizonqa.mode=off` only when you want the mod on the classpath without commands, discovery, runner behavior, or test visuals. Batch execution and JUnit XML are server-side.

## Source layout

Recommended layout for a mod named `mymod`:

```text
src/main/java/.../tests/
  multiblock/<machine>/             ← single-mod multiblock tests
  compatibility/<modA>_<modB>/      ← cross-mod scenarios
src/main/resources/assets/mymod/horizonqastructures/
  ebf.json
  ebf_tiles.nbt
```

!!! warning "Do not mirror `examples/` in a consumer mod"

    Framework examples belong only in this repository's `examples/` tree. Copying that layout name into a gameplay mod produces an undifferentiated test dump that does not scale. See [Package layout](../reference/package-layout.md) and [Design principles](../contributing/principles.md).

## Test discovery

Discovery is ASM-based at server start:

- Every class annotated `@GameTestHolder` is scanned.
- Every **static** method annotated `@GameTest` with signature `void name(GameTestHelper)` is registered.
- Test id format: `<holder.value>:<SimpleClassName>.<methodName>`

There is no manual registration list and no service-file step.

## Structure assets

Templates load from the classpath:

```text
/assets/<namespace>/horizonqastructures/<path>.json
/assets/<namespace>/horizonqastructures/<path>_tiles.nbt   (optional)
```

`@GameTest(template = "ebf")` declared on a class with `@GameTestHolder("mymod")` resolves to `mymod:ebf`. Use `template = "othermod:shared/cell"` to reference a fully qualified template from another mod, or `templatePrefix` on the holder to share a prefix across a class.

## Optional: copy patterns from the examples

The `examples` subproject is the canonical reference:

- `BasicTests` — assertions, sequences, optional tests.
- `GTNHExampleTests` — EBF formation, EU supply, maintenance gating, synthetic recipes.
- `StructureTests` — template placement and block-level assertions.

Run `./gradlew :examples:runServer` to iterate against them.

## Publishing

Horizon-QA is **not** bundled in release jars of gameplay mods; keep it on `devOnly` / CI classpaths. Shipping structure JSON in a release jar is unusual unless you deliberately publish test assets to players.
