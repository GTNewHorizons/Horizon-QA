---
title: Getting started
description: Add Horizon-QA to a GTNH-style Forge mod and run your first GameTest.
tags:
  - getting-started
---

# Getting started

Horizon-QA ships as a Forge mod (`horizonqa`) that other mods depend on at **development and CI time**. Tests are plain Java classes discovered at runtime via `@GameTestHolder` / `@GameTest` — there is no separate registration step.

!!! abstract "What you will have at the end"

    A `runServer` task that launches Minecraft with `-Dhorizonqa.mode=ci`, runs every test in your mod, leaves failed cells in the world for inspection, and writes a JUnit XML report next to the server directory.

## Prerequisites

- A **GTNH-style** Gradle mod project (uses the GTNH Gradle convention plugin).
- **Java 8** bytecode for mod code. The docs site and CI tooling can use newer JDKs.
- Familiarity with Forge 1.7.10 dev environments — `runClient` and `runServer` should already work for your mod.

## Learning path

The three pages below are sequenced; skim them in order if you are new to the framework.

1. [Enable & run](enable-and-run.md) — JVM flag, dedicated world, `/horizonqa` commands, examples mod.
2. [Your first test](first-test.md) — minimal `@GameTest`, assertions, success conditions.
3. [Mod project setup](mod-setup.md) — Gradle dependency, classpath assets, package layout.

## Examples mod

The repository includes `examples/`, a Gradle subproject that depends on Horizon-QA and GT5-Unofficial. Use it as a runnable reference:

```text
examples/src/main/java/.../tests/                       # @GameTest classes
examples/src/main/resources/assets/<namespace>/horizonqastructures/
```

See [Examples mod](../contributing/examples-mod.md) for the Gradle wiring.

## Where to go next

| Goal                                | Page                                                 |
|-------------------------------------|------------------------------------------------------|
| Export and reference structures     | [Structure templates](../guide/structures.md)        |
| Smelt a recipe in an EBF            | [GTNH multiblock API](../guide/gtnh-api.md)          |
| Wire CI                             | [CI & JUnit reports](../guide/ci.md)                 |
| Look up `@GameTest` attributes      | [Annotations](../reference/annotations.md)           |
