---
title: Commands
description: /gametest subcommand reference, permissions, and export requirements.
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
| `runthis`   | `/horizonqa runthis`            | Re-run the test cell in your line of sight (≤ 64 blocks)                  |
| `runthat`   | `/horizonqa runthat`            | Re-run the nearest known test cell                                        |
| `pos`       | `/horizonqa pos`                | Print world and test-relative coordinates; suggest `helper.absolute(...)` |
| `clearall`  | `/horizonqa clearall`           | Clear all placed test cells and overlays                                  |
| `clear`     | `/horizonqa clear`              | Clear Horizon Wand's selected position                                    |
| `export`    | `/horizonqa export <name>`      | Export the wand selection to `horizonqastructures/`                       |

Tab-completion is wired for subcommands, full test ids on `run`, and namespaces on `runall`.

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
