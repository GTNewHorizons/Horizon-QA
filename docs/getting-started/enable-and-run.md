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
./gradlew runServer --mcJvmArgs="-Dhorizonqa.mode=interactive"
./gradlew runServer --mcJvmArgs="-Dhorizonqa.mode=ci"
./gradlew runServer --mcJvmArgs="-Dhorizonqa.mode=off"
```

`interactive` enables commands, overlays, and manual test runs. It is the default when `horizonqa.mode` is not set.

`ci` enables the deterministic headless path:

- The dedicated **GameTest** void world type is used by default.
- ASM-based discovery runs across every `@GameTestHolder` class on the classpath.
- Selected tests run automatically.
- `TEST-horizonqa.xml` and `horizonqa-result.json` are written before the server exits.

`off` loads the mod but disables commands, discovery, runner behavior, and test visuals.

Modes are presets. You can override the main runtime choices without adding another mode:

```text
./gradlew runServer --mcJvmArgs="-Dhorizonqa.mode=ci -Dhorizonqa.world=normal -Dhorizonqa.stopServer=false"
./gradlew runServer --mcJvmArgs="-Dhorizonqa.mode=ci -Dhorizonqa.autoRun=false"
./gradlew runServer --mcJvmArgs="-Dhorizonqa.mode=ci -Dhorizonqa.autoRun=false -Dhorizonqa.world=normal -Dhorizonqa.gridOrigin=0,128,0"
```

!!! tip "Pick the mode for the job"

    Use the default `interactive` mode for local authoring, `ci` for automated server runs, and `ci` with `-Dhorizonqa.autoRun=false` when you want report files from a manually-started batch.

## Run the examples

From the repository root, with GTNH caches already configured:

```bash
./gradlew --info --stacktrace :examples:runServer
```

`runServer` is provided by Retrofuturagradle. When you need to pass a JVM flag, forward it to the Minecraft server via `--mcJvmArgs`. Passing `-Dhorizonqa.mode=ci` directly to Gradle sets it on the Gradle daemon, where the runner never sees it.

For a CI-style example run:

```bash
./gradlew --info --stacktrace :examples:runServer --mcJvmArgs="-Dhorizonqa.mode=ci -Dhorizonqa.reportDir=build/horizonqa"
```

In-game (operator permission level **2**):

| Command                         | Purpose                                                                 |
|---------------------------------|-------------------------------------------------------------------------|
| `/horizonqa runall`             | Run every discovered test                                               |
| `/horizonqa runall <namespace>` | Run tests whose id starts with `<namespace>:`                           |
| `/horizonqa run <testId>`       | Run one test by id, e.g. `horizonqaexamples:BasicTests.simplePass`      |
| `/horizonqa runfailed`          | Re-run only the tests that failed in the last batch                     |
| `/qa`                           | Alias for `/horizonqa`                                                  |

In `ci` mode with `-Dhorizonqa.autoRun=false`, `/horizonqa run`, `/horizonqa runall`, and `/horizonqa runfailed` write **`TEST-horizonqa.xml`** and **`horizonqa-result.json`** after the batch completes. The files are written in the working directory unless report paths are overridden. See [CI & JUnit reports](../guide/ci.md).

## Horizon Wand

A creative-tab item used to define export bounds and label important coordinates.

1. ++left-button++ a block → position 1.
2. ++right-button++ a block → position 2. Right-click also works at range via the targeted block, and sneaking selects the adjacent (air) block instead, which is useful for capturing clearance above a multiblock.
3. Hold the wand and press ++l++ to label the targeted coordinate. Press ++l++ on an existing label to rename it or remove it. Sneak while pressing ++l++ to label the adjacent air coordinate.
4. `/horizonqa labels list` checks the labels before export.
5. `/horizonqa export <name>` → writes `horizonqastructures/<name>.json` and, when needed, `<name>.snbt` or `<name>.nbt` under the server directory.

Move the exported files into `src/main/resources/assets/<modid>/horizonqastructures/` in your mod. Use `helper.pos("label_name")` in tests instead of repeating raw local coordinates. Full export details: [Structure templates](../guide/structures.md).

To fix an existing fixture, target the placement coordinate and run `/horizonqa load <modid:path/to/template>`. Horizon-QA places the structure, restores labels onto the wand, and remembers the export path; after editing, `/horizonqa export` writes the updated files under the same server `horizonqastructures/` path.

## Interactive debugging

After `/horizonqa runall`, failed cells **stay placed** on the grid with their overlays. These commands are designed for the in-world triage loop:

| Command                  | Purpose                                                                     |
|--------------------------|-----------------------------------------------------------------------------|
| `/horizonqa tp <testId>` | Teleport to the placed cell for a specific test id                          |
| `/horizonqa pos`         | Print world + test-relative coordinates; suggest `helper.absolute(x, y, z)` |
| `/horizonqa runthis`     | Re-run the test cell you are standing inside                                |
| `/horizonqa runthat`     | Re-run the test cell you are looking at                                     |
| `/horizonqa clearall`    | Remove placed test cells and overlays                                       |

!!! tip "Iterate without restarting"

    Edit a test, recompile (hotswap or `gradlew classes`), then `/horizonqa runthis` on the failed cell. You do not need to restart the server for most code changes.

The full triage workflow (reading the event trace, failure signatures, in-game reproduction) is in [Debugging failed tests](../guide/debugging.md).

## Disable event recording (optional)

Event logging is on by default. Disable only for micro-benchmarking; you lose your main failure diagnostic.

```text
-Dhorizonqa.events=off
```

See [Test event log](../reference/events.md).
