---
title: Enable & run
description: Activate Horizon-QA with a JVM flag and drive it via /horizonqa commands in a dev server.
tags:
  - getting-started
  - commands
---

# Enable & run

Horizon-QA is **opt-in at the JVM level**. Normal dev clients are unaffected until you set the flag, so adding the mod to a workspace is safe even when you do not intend to run tests.

## Enable the framework

Add this system property to the **server** JVM (Gradle `runServer`, CI script, or IDE run configuration):

```text
-Dgtnh.horizonqa=true
```

When the flag is present:

- The dedicated **GameTest** world type is registered.
- ASM-based discovery runs across every `@GameTestHolder` class on the classpath.
- `/horizonqa` commands and batch execution become available.

Without the flag the mod still loads, but the runner mixins remain inert.

!!! tip "Add it once, gate later"

    Set `-Dgtnh.horizonqa=true` permanently on your `runServer` task. The flag is cheap when no tests run, and forgetting it is the most common "why are my tests not found?" cause.

## Run the examples

From the repository root, with GTNH caches already configured:

```bash
./gradlew --info --stacktrace :examples:runServer --mcJvmArgs="-Dgtnh.horizonqa=true"
```

`runServer` is provided by Retrofuturagradle, which forwards JVM flags to the Minecraft server only via `--mcJvmArgs`. Passing `-Dgtnh.horizonqa=true` directly to Gradle sets it on the Gradle daemon, where the runner never sees it.

In-game (operator permission level **2**):

| Command                           | Purpose                                                              |
|-----------------------------------|----------------------------------------------------------------------|
| `/horizonqa runall`               | Run every discovered test                                            |
| `/horizonqa runall <namespace>`   | Run tests whose id starts with `<namespace>:`                        |
| `/horizonqa run <testId>`         | Run one test by id, e.g. `horizonqaexamples:BasicTests.passImmediately` |
| `/horizonqa runfailed`            | Re-run only the tests that failed in the last batch                  |
| `/qa`                             | Alias for `/horizonqa`                                               |

After a batch completes, the server writes **`TEST-horizonqa.xml`** in the working directory (typically the run folder). See [CI & JUnit reports](../guide/ci.md).

## Horizon Wand

A creative-tab item used to define export bounds.

1. ++left-button++ a block → position 1.
2. ++right-button++ a block → position 2.
3. `/horizonqa export <name>` → writes `horizonqastructures/<name>.json` and `<name>_tiles.nbt` under the server directory.

Move the exported files into `src/main/resources/assets/<modid>/horizonqastructures/` in your mod. Full export details: [Structure templates](../guide/structures.md).

## Interactive debugging

After `/horizonqa runall`, failed cells **stay placed** on the grid with their overlays. These commands are designed for the in-world triage loop:

| Command               | Purpose                                                                                |
|-----------------------|----------------------------------------------------------------------------------------|
| `/horizonqa pos`      | Print world + test-relative coordinates; click-to-copy `helper.absolute(x, y, z)`      |
| `/horizonqa runthis`  | Re-run the test cell you are looking at                                                |
| `/horizonqa runthat`  | Re-run the nearest test cell                                                           |
| `/horizonqa clearall` | Remove placed test cells and overlays                                                  |

!!! tip "Iterate without restarting"

    Edit a test, recompile (hotswap or `gradlew classes`), then `/horizonqa runthis` on the failed cell. You do not need to restart the server for most code changes.

## Disable event recording (optional)

Event logging is on by default. Disable only for micro-benchmarking; you lose your main failure diagnostic.

```text
-Dhorizonqa.events=off
```

See [Test event log](../reference/events.md).
