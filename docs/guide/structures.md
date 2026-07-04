---
title: Structure templates
description: Export, format, placement, and rotation of GameTest structure templates.
tags:
  - guides
  - structures
---

# Structure templates

Structures are compact JSON layouts plus optional structure data for tile entities and entities. They are versioned alongside your mod jar and referenced by name from `@GameTest`.

## On-disk layout

After export or hand-authoring:

```text
src/main/resources/assets/<namespace>/horizonqastructures/
  my_cell.json
  my_cell.snbt   (optional; tile entity and entity data, when text-safe)
  my_cell.nbt    (optional fallback; tile entity and entity data)
```

Runtime resolution is by classpath:

```text
/assets/<namespace>/horizonqastructures/<path>.json
/assets/<namespace>/horizonqastructures/<path>.snbt   (optional)
/assets/<namespace>/horizonqastructures/<path>.nbt    (optional fallback)
```

Reference from tests:

```java
@GameTestHolder("mymod")
public class MyTests {
    @GameTest(template = "multiblock/ebf") // resolves to mymod:multiblock/ebf
}
```

## Export workflow

```mermaid
flowchart LR
    A[Build the structure<br/>in a dev world] --> B[Wand: left-click pos1,<br/>right-click pos2]
    B --> C[Press L to label<br/>important coordinates]
    C --> D["/horizonqa export name"]
    D --> E[Server writes JSON + structure data<br/>under serverDir/horizonqastructures/]
    E --> F[Move into<br/>assets/modid/horizonqastructures/]
```

1. Build the structure in a dev world with Horizon-QA enabled.
2. Select bounds with the **Horizon Wand**: ++left-button++ for pos1, ++right-button++ for pos2.
3. Hold the wand and press ++l++ to label important coordinates such as `controller`, `input_bus`, or `energy_hatch`. Press ++l++ on an existing label to rename it or remove it. Sneak while pressing ++l++ to label the adjacent air block.
4. Run `/horizonqa labels list` and fix any labels outside the selection.
5. Run `/horizonqa export <name>`. Allowed characters: letters, digits, `_`, `-`.
6. The server writes to `<serverDir>/horizonqastructures/`:
   - `<name>.json` with the block palette and layers.
   - `<name>.snbt` with tile entity and non-player entity data, if the generated text round-trips losslessly.
   - `<name>.nbt` instead of `.snbt` when the NBT contains data that Minecraft 1.7.10's SNBT parser cannot represent safely, such as compound keys containing `:`.
7. Move the exported files into your mod's `assets/<modid>/horizonqastructures/`.

!!! tip "Label the coordinates the Java code will name"

    If a test will refer to a controller, hatch, bus, button, sensor, or expected output block, put that name in the template. Use `/horizonqa pos` as a temporary debugging aid, not as the usual way to author long-lived test coordinates.

## Format

Templates use `format_version: 1`, a palette keyed by single-character symbols, and a `layers` array in Y-major order. The loader throws `IOException` with explicit messages for missing layers and unknown palette keys; on a load failure the server log identifies the file and the offending key.

The optional structure data file uses `tiles` as a list of tile entity compounds and `entities` as a list of non-player entity compounds; both are merged at placement time. New exports prefer text `<path>.snbt`, but only write it after parsing the generated text back and confirming the NBT tree is unchanged. If that round-trip is not lossless, the exporter writes combined binary `<path>.nbt`. For compatibility, the loader also accepts older `<path>_tiles.nbt` and `<path>_entities.nbt` files.

## Coordinate labels

Templates may include optional coordinate labels:

```json
{
  "format_version": 1,
  "size": [1, 1, 1],
  "palette": {
    "A": {"name": "minecraft:stone", "meta": 0, "label": "Stone"}
  },
  "layers": [
    [
      "A"
    ]
  ],
  "annotations": {
    "labels": {
      "stone": [0, 0, 0]
    }
  }
}
```

Labels are optional for loading and running tests, but any labels present in a template are validated. Names must match `[A-Za-z_][A-Za-z0-9_]*`; prefer `snake_case`. Coordinates are template-relative `[x, y, z]` and must be inside `size`.

Use labels from Java:

```java
helper.assertBlockPresent(Blocks.stone, helper.pos("stone"));
TestPos controller = helper.pos("controller");
TestPos controllerWorld = helper.absolute("controller");
```

`helper.pos("name")` returns test-relative coordinates with `@GameTest(rotation = ...)` applied. `helper.absolute("name")` returns world coordinates after the same rotation. Asking for an undefined label fails the test as an infrastructure error with type `LABEL_ERROR`.

## Placement in the grid

The batch runner places each test's template into a dedicated grid cell with margin for clearance. CI defaults to Horizon-QA's void world, but `-Dhorizonqa.world=normal` leaves the server's configured or existing world type in place, and `-Dhorizonqa.gridOrigin=x,y,z` moves the grid start. Structure placement emits `StructurePlaced` in the [event log](../reference/events.md), so a missing structure surfaces in CI without a manual rerun.

## Rotation

Set `rotation` on `@GameTest` (values `0-3`) to validate that role indices and `Multiblock` wiring still match after 90° steps. Blocks, tile entities, and exported entities are rotated together. If a test only passes at `rotation = 0`, document why in a short comment; that asymmetry almost always points at a coordinate that should have been a role lookup.

## Empty templates

Omit `template` (or use `template = ""`) for tests that only need void space: block-placement smoke tests, helper API checks, and the like.

## Choosing between `setBlock` and an exported template

Every test falls into one of two categories, and the right template strategy follows from which one you are writing.

### Logic tests: empty template + `setBlock`

A **logic test** verifies behaviour that does not depend on a specific world layout. The test builds exactly the state it needs via `setBlock`, runs the logic under test, and asserts the outcome. No template file exists on disk.

```java
@GameTest(timeoutTicks = 20)
public static void chestInsertAndAssert(GameTestHelper helper) {
    helper.setBlock(0, 0, 0, Blocks.chest);
    helper.startSequence()
        .thenIdle(1)
        .thenExecute(() -> {
            helper.insertItem(0, 0, 0, new ItemStack(Items.diamond, 5));
            helper.assertInventoryContains(0, 0, 0, new ItemStack(Items.diamond, 5));
        })
        .thenSucceed();
}
```

The test owns every block it places. When the system under test changes, the test changes with it; there is no template to re-export.

Typical subjects:

- Helper API correctness (`setBlock`, `destroyBlock`, `assertBlockPresent`).
- Single-block tile-entity interactions (chest insertion, furnace smelting).
- Redstone or signal propagation with a handful of blocks.
- Any scenario where the interesting part is the *sequence of actions*, not the structure they act on.

### Structure tests: exported template

A **structure test** validates behaviour that emerges from a pre-built world layout: formed multiblocks, multi-tile wiring, spatial relationships between hatches. The template is exported once with `/horizonqa export` and loaded at test time.

```java
@GameTest(template = "ebf", timeoutTicks = 1500, batch = "gtnh")
public static void testTitaniumSmelting(GameTestHelper helper) {
    Multiblock ebf = helper.gtnh().multiblock(helper.pos("controller"));
    ebf.assertFormed();
    ebf.fixMaintenance();
    ebf.inputBus(0)
        .insert(Materials.Nickel.getDust(1), Materials.Aluminium.getDust(3))
        .programmedCircuit(0);
    ebf.energyHatch(0).supply(TierEU.EV, 1, 900);
    ebf.runRecipe();
    ebf.outputs().assertContains(Materials.NickelAluminide.getIngots(4));
    helper.succeed();
}
```

The test assumes the structure is already correct and focuses on what happens *inside* it. Rebuilding an EBF block-by-block with `setBlock` would duplicate the template's information, couple the test to layout coordinates, and break whenever a block id or metadata changes.

Typical subjects:

- Multiblock formation and recipe processing.
- Hatch roles, maintenance, and energy supply across a formed machine.
- Negative-formation tests (e.g. `ebf_no_coils`) that assert a machine *does not* form.
- Any scenario where the interesting part is the *structure itself* or how a machine behaves within it.

### Decision guide

| Signal                                       | Strategy              |
|----------------------------------------------|-----------------------|
| Fewer than ~5 blocks, simple arrangement     | `setBlock`            |
| Testing API helpers, not world state         | `setBlock`            |
| Multiblock or complex tile-entity wiring     | Exported template     |
| Layout accuracy is *part of* the assertion   | Exported template     |
| Test must survive cross-version block renames | `setBlock`            |
| Rotation coverage is required                | Exported template     |

When in doubt, ask: *"If the layout changed tomorrow, should this test break?"* If yes, the layout is load-bearing: export a template so the test guards it. If no, build the state inline so the test stays decoupled.

## Examples in this repo

| Template                                      | Purpose                            |
|-----------------------------------------------|------------------------------------|
| `horizonqaexamples:single_stone`              | Single block                       |
| `horizonqaexamples:stone_platform`            | Small platform                     |
| `horizonqaexamples:ebf`                       | Formed EBF with hatches            |
| `horizonqaexamples:ebf_no_coils`              | Intentionally invalid EBF          |
| `horizonqaexamples:distillation_tower_4`      | Multi-output bus routing           |
| `horizonqaexamples:cleanroom`                 | Cleanroom efficiency over time     |

Source: `examples/src/main/resources/assets/horizonqaexamples/horizonqastructures/`.
