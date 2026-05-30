---
title: Enable & run
description: Use Horizon-QA interactively by default or switch to headless CI mode.
tags:
  - getting-started
  - commands
---

# Enable & run

Horizon-QA starts in **interactive** mode by default. Adding the mod to a workspace enables `/horizonqa` commands, test discovery, and visual debugging without extra JVM flags.

## Select a mode

Set `horizonqa.mode` on the **server** JVM when you need a mode other than the default:

```text
-Dhorizonqa.mode=off
-Dhorizonqa.mode=interactive
-Dhorizonqa.mode=ci
```

`off` loads the mod but disables commands, discovery, runner behavior, and test visuals.

`interactive` enables commands, overlays, and manual test runs. It is the default when `horizonqa.mode` is not set.

`ci` enables the deterministic headless path:

- The dedicated **GameTest** world type is registered.
- ASM-based discovery runs across every `@GameTestHolder` class on the classpath.
- All discovered tests run automatically.
- A JUnit report is written before the server exits.

!!! tip "Pick the mode for the job"

    Use the default `interactive` mode for local authoring and `ci` for automated server runs.

## Run the examples

From the repository root, with GTNH caches already configured:

```bash
./gradlew --info --stacktrace :examples:runServer
```

`runServer` is provided by Retrofuturagradle. When you do need to pass a JVM flag, such as CI mode, forward it to the Minecraft server via `--mcJvmArgs`. Passing `-Dhorizonqa.mode=ci` directly to Gradle sets it on the Gradle daemon, where the runner never sees it.

In-game (operator permission level **2**):

| Command                           | Purpose                                                              |
|-----------------------------------|----------------------------------------------------------------------|
| `/horizonqa runall`               | Run every discovered test                                            |
| `/horizonqa runall <namespace>`   | Run tests whose id starts with `<namespace>:`                        |
| `/horizonqa run <testId>`         | Run one test by id, e.g. `horizonqaexamples:BasicTests.passImmediately` |
| `/horizonqa runfailed`            | Re-run only the tests that failed in the last batch                  |
| `/qa`                             | Alias for `/horizonqa`                                               |

After a batch completes, the server writes **`TEST-horizonqa.xml`** and **`horizonqa-result.json`** in the working directory (typically the run folder). See [CI & JUnit reports](../guide/ci.md).

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
