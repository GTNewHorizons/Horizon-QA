---
title: Getting started
description: Add Horizon-QA to a GTNH-style Forge mod, write one test, and run it locally or in CI.
---

# Getting started

This path takes a GTNH-style Forge mod from no test framework to one passing Horizon-QA test. Tests are plain Java methods discovered at server startup, so there is no registration file or separate test launcher to maintain.

## Before you begin

You need:

- A GTNH Gradle convention project whose `runClient` and `runServer` tasks already work.
- Horizon-QA on the development and CI classpath.
- Mod code that targets Java 8 bytecode. Your development toolchain may use the modern Java setup supported by the GTNH build conventions.

## The shortest path

<div class="grid cards horizon-flow" markdown>

-   :material-package-variant:{ .lg .middle } **Add the dependency**

    ---

    Put the Horizon-QA dev artifact on the compile and runtime classpath without publishing it with your mod.

-   :material-test-tube:{ .lg .middle } **Write one test**

    ---

    Create a holder class, add a public static `@GameTest` method, and finish with one success path.

-   :material-play-circle-outline:{ .lg .middle } **Run locally**

    ---

    Start the server in the default interactive mode and launch the test with `/horizonqa run`.

-   :material-source-branch-check:{ .lg .middle } **Automate**

    ---

    Use CI mode to select tests, write JUnit XML and status JSON, and return a stable exit code.

</div>

1. [Add Horizon-QA to your mod](mod-setup.md)
2. [Write your first test](first-test.md)
3. [Run tests](enable-and-run.md)

At the end, you will have a server-run test that can be launched interactively and through `-Dhorizonqa.mode=ci`.

## Use the examples as a reference

The repository includes an `examples/` Gradle subproject that runs against Horizon-QA and GT5-Unofficial:

```text
examples/src/main/java/.../tests/
examples/src/main/resources/assets/horizonqaexamples/horizonqastructures/
```

Run it from the repository root:

```bash
./gradlew :examples:runServer
```

The examples are framework demonstrations, not a package layout to copy into a consumer mod. See [Examples mod](../contributing/examples-mod.md) for the class-by-class map.

## Common next steps

| Goal | Read |
|---|---|
| Understand discovery, batches, ticks, and reporting | [Learn Horizon-QA](../concepts/index.md) |
| Export a multiblock fixture | [Structure templates](../guide/structures.md) |
| Drive a GregTech controller | [GTNH multiblock API](../guide/gtnh-api.md) |
| Assert that a bad state never occurs | [Negative assertions](../guide/negative-tests.md) |
| Publish reports in automation | [CI and JUnit reports](../guide/ci.md) |
| Fix missing commands, tests, templates, or reports | [Setup troubleshooting](../guide/troubleshooting.md) |
| Diagnose a failure | [Debugging failed tests](../guide/debugging.md) |
