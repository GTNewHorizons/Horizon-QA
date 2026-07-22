---
title: Commands
description: /horizonqa subcommand reference, permissions, and export requirements.
---

# Commands

Primary command: **`/horizonqa`** (alias **`/qa`**). Requires permission level **2** (operator).

## Subcommands

| Subcommand  | Usage                           | Description                                                               |
|-------------|---------------------------------|---------------------------------------------------------------------------|
| `run`       | `/horizonqa run <testId>`       | Run a single test by full id                                              |
| `runall`    | `/horizonqa runall [namespace]` | Run all tests, or filter by id prefix `<namespace>:`                      |
| `runfailed` | `/horizonqa runfailed`          | Re-run failures remembered by the current mode                            |
| `tp`        | `/horizonqa tp <testId>`        | Teleport to the placed cell for a test ID                                 |
| `runthis`   | `/horizonqa runthis`            | Re-run the test cell you are standing inside                              |
| `runthat`   | `/horizonqa runthat`            | Re-run the test cell in your line of sight (<= 64 blocks)                 |
| `pos`       | `/horizonqa pos`                | Print world and test-local coordinates; offer an absolute-position snippet |
| `clearall`  | `/horizonqa clearall`           | Clear all placed test cells and overlays                                  |
| `load`      | `/horizonqa load <namespace:path> [rotation]` | Place a template for editing and arm the wand for re-export     |
| `clear`     | `/horizonqa clear`              | Clear Horizon Wand's selected positions and labels                        |
| `export`    | `/horizonqa export [name]`      | Export the wand selection to `horizonqastructures/`                       |
| `label`     | `/horizonqa label <name>`       | Label the coordinate currently targeted by the Horizon Wand               |
| `labels`    | `/horizonqa labels <subcommand>` | List, remove, or clear labels on the current Horizon Wand                 |

Tab-completion is wired for subcommands, full test ids on `run`, placed test ids on `tp`, namespaces on `runall`, discovered template names on `load`, and label names on `labels remove`.

When the server starts in a non-interactive reported-batch configuration, such as `-Dhorizonqa.mode=ci -Dhorizonqa.autoRun=false`, `run`, `runall`, and `runfailed` use the CI batch runner and write JUnit XML plus status JSON after the batch completes. The server stays running unless `-Dhorizonqa.stopServer=true` is set.

In normal interactive mode, those commands launch the selected tests directly for inspection. Interactive launches do not order tests by `GameTest.batch()` and do not invoke `@BeforeBatch` or `@AfterBatch`.

`runfailed` is mode-sensitive:

- Interactive mode reruns tests that are still marked failed in the current interactive session. A successful rerun removes a test from that set.
- Reported mode reruns failures remembered from the most recent reported batch.

`runthis`, `runthat`, `pos`, `clearall`, and `load` are restricted to interactive features. `tp` also depends on cells created by the interactive session and has no reported-batch cells to target.

Only one batch runner can be active at a time. If an automatic or reported batch is running, commands that launch, relaunch, or clear tests (`run`, `runall`, `runfailed`, `runthis`, `runthat`, and `clearall`) are rejected until the active batch finishes.

## Export requirements

- Must be executed by a **player** (not the console).
- **Horizon Wand** in hand or inventory.
- `pos1` and `pos2` set on the wand.
- `<name>` may be omitted after `/horizonqa load`; otherwise pass a template path.
- Template path segments may use letters, digits, `_`, `-`, and `.` with `/` between segments. Empty, `.`, and `..` segments are rejected.
- Any labels stored on the wand must be inside the selected bounds.

Output directory: `<serverDir>/horizonqastructures/`.

Exported files:

- `<name>.json` for the version 2 block layout.
- `<name>.snbt` for tile entity and non-player entity data, when the selection has any and the generated text round-trips losslessly.
- `<name>.nbt` instead of `.snbt` when the structure data cannot be represented safely in Minecraft 1.7.10 SNBT.

ItemStacks anywhere in the exported tile entity or entity data use registry-name `id` values rather than environment-specific numeric IDs. Loading resolves those names against the active item registry; a missing item fails the template with its registry name and NBT path.

Coordinate labels are written into the JSON as optional `annotations.labels` entries. Tests can read them with `helper.pos("name")` for test-local coordinates or `helper.absolute("name")` for world coordinates. Missing labels are reported as infrastructure errors with type `LABEL_ERROR`.

## Template edit loop

Use `load` when an existing template needs a quick in-world fix.

```text
/qa load mymod:machines/ebf
```

The template is placed at the coordinate targeted by the player. Sneak while targeting a block to use the adjacent air block, matching Horizon Wand selection behavior. Optional `rotation` is `0`, `1`, `2`, or `3` quarter-turns clockwise.

After placement, Horizon-QA sets the wand selection to the placed bounds, restores template labels as world coordinates, and remembers the export path. Make edits in-game, then run:

```text
/qa export
```

The export writes `horizonqastructures/machines/ebf.json` plus any `.snbt` or `.nbt` structure data under the server directory. Move the updated files back into `assets/mymod/horizonqastructures/`.

### Migrating a version 1 template

Version 1 structure data may contain numeric ItemStack IDs from the modpack that exported it. Horizon-QA rejects those stacks during test execution. Perform a one-time migration in that original environment:

1. Start the interactive server with `-Dhorizonqa.allowLegacyNumericItemIds=true`.
2. Run `/horizonqa load <namespace:path>`.
3. Run `/horizonqa export` and replace the packaged template files with the version 2 export.

For a Gradle server run, pass the property to Minecraft rather than the Gradle daemon:

```bash
./gradlew runServer \
  --mcJvmArgs="-Dhorizonqa.allowLegacyNumericItemIds=true"
```

The property trusts the current numeric item registry only for interactive `/horizonqa load`. It has no effect on CI, reported batches, or interactive test runs. Use it only in the original ID environment for migration, and remove it afterward.

## Label commands

Labels live on the Horizon Wand until the structure is exported.

| Command                          | Purpose                                                       |
|----------------------------------|---------------------------------------------------------------|
| `/horizonqa label <name>`        | Label the targeted coordinate, or rename the label at it      |
| `/horizonqa labels list`         | Show all labels on the wand and warn about outside labels     |
| `/horizonqa labels remove <name>` | Remove one label                                             |
| `/horizonqa labels clear`        | Remove all labels from the wand                              |

Label names must match `[A-Za-z_][A-Za-z0-9_]*`; prefer `snake_case` names such as `controller`, `input_bus`, and `energy_hatch`.

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

See [Run tests](../getting-started/enable-and-run.md) for the broader command flow and [CI and JUnit reports](../guide/ci.md) for reported execution.
