---
title: Commands
description: /horizonqa subcommand reference, permissions, and export requirements.
tags:
  - reference
  - commands
---

# Commands

Primary command: **`/horizonqa`** (alias **`/qa`**). Requires permission level **2** (operator).

## Subcommands

| Subcommand  | Usage                           | Description                                                               |
|-------------|---------------------------------|---------------------------------------------------------------------------|
| `run`       | `/horizonqa run <testId>`       | Run a single test by full id                                              |
| `runall`    | `/horizonqa runall [namespace]` | Run all tests, or filter by id prefix `<namespace>:`                      |
| `runfailed` | `/horizonqa runfailed`          | Re-run tests that failed in the previous batch                            |
| `tp`        | `/horizonqa tp <testId>`        | Teleport to the placed cell for a test id                                 |
| `runthis`   | `/horizonqa runthis`            | Re-run the test cell you are standing inside                              |
| `runthat`   | `/horizonqa runthat`            | Re-run the test cell in your line of sight (<= 64 blocks)                 |
| `pos`       | `/horizonqa pos`                | Print world and test-relative coordinates; suggest `helper.absolute(...)` |
| `clearall`  | `/horizonqa clearall`           | Clear all placed test cells and overlays                                  |
| `clear`     | `/horizonqa clear`              | Clear Horizon Wand's selected position                                    |
| `export`    | `/horizonqa export <name>`      | Export the wand selection to `horizonqastructures/`                       |

Tab-completion is wired for subcommands, full test ids on `run`, placed test ids on `tp`, and namespaces on `runall`.

When the server starts in a non-interactive reported-batch configuration, such as `-Dhorizonqa.mode=ci -Dhorizonqa.autoRun=false`, `run`, `runall`, and `runfailed` use the CI batch runner and write JUnit XML plus status JSON after the batch completes. The server stays running unless `-Dhorizonqa.stopServer=true` is set. Interactive cell commands such as `runthis`, `runthat`, `pos`, and `clearall` are available only in interactive mode.

Only one batch runner can be active at a time. If an automatic or reported batch is running, commands that launch, relaunch, or clear tests (`run`, `runall`, `runfailed`, `runthis`, `runthat`, and `clearall`) are rejected until the active batch finishes.

## Export requirements

- Must be executed by a **player** (not the console).
- **Horizon Wand** in hand or inventory.
- `pos1` and `pos2` set on the wand.
- `<name>` characters: letters, digits, `_`, `-`.

Output directory: `<serverDir>/horizonqastructures/`.

## Typical workflows

=== "Single test debug"

    ```text
    /horizonqa run horizonqaexamples:GTNHExampleTests.testTitaniumSmelting
    ```

=== "Full mod suite"

    ```text
    /horizonqa runall mymod
    ```

=== "After a CI failure"

    ```text
    /horizonqa runfailed
    ```

See [Enable & run](../getting-started/enable-and-run.md) for the broader command flow and [CI & JUnit reports](../guide/ci.md) for the headless equivalent.
