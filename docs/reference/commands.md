---
title: Commands
description: /gametest subcommand reference, permissions, and export requirements.
tags:
  - reference
  - commands
---

# Commands

Primary command: **`/gametest`** (alias **`/gt`**). Requires permission level **2** (operator).

## Subcommands

| Subcommand   | Usage                                | Description                                                              |
|--------------|--------------------------------------|--------------------------------------------------------------------------|
| `run`        | `/gametest run <testId>`             | Run a single test by full id                                             |
| `runall`     | `/gametest runall [namespace]`       | Run all tests, or filter by id prefix `<namespace>:`                     |
| `runfailed`  | `/gametest runfailed`                | Re-run tests that failed in the previous batch                           |
| `runthis`    | `/gametest runthis`                  | Re-run the test cell in your line of sight (≤ 64 blocks)                 |
| `runthat`    | `/gametest runthat`                  | Re-run the nearest known test cell                                       |
| `pos`        | `/gametest pos`                      | Print world and test-relative coordinates; suggest `helper.absolute(...)` |
| `clearall`   | `/gametest clearall`                 | Clear all placed test cells and overlays                                 |
| `export`     | `/gametest export <name>`            | Export the wand selection to `gameteststructures/`                       |

Tab-completion is wired for subcommands, full test ids on `run`, and namespaces on `runall`.

## Export requirements

- Must be executed by a **player** (not the console).
- **Horizon Wand** in hand or inventory.
- `pos1` and `pos2` set on the wand.
- `<name>` characters: letters, digits, `_`, `-`.

Output directory: `<serverDir>/gameteststructures/`.

## Typical workflows

=== "Single test debug"

    ```text
    /gametest run gametestexamples:GTNHExampleTests.testTitaniumSmelting
    ```

=== "Full mod suite"

    ```text
    /gametest runall mymod
    ```

=== "After a CI failure"

    ```text
    /gametest runfailed
    ```

See [Enable & run](../getting-started/enable-and-run.md) for the broader command flow and [CI & JUnit reports](../guide/ci.md) for the headless equivalent.
