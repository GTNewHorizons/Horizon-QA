---
title: Horizon-QA
description: End-to-end GameTest framework for GT New Horizons on Minecraft 1.7.10.
tags:
  - overview
---

# Horizon-QA

<div class="horizon-hero" markdown>

**Horizon-QA** is an end-to-end testing framework for GTNH. It exposes a **GameTest**-style API on Minecraft **1.7.10**: real server, real blocks, real GregTech machines. Recipe and machine logic are never mocked.

[:octicons-rocket-24: Get started](getting-started/index.md){ .md-button .md-button--primary }
[:octicons-code-24: Javadoc API](https://GTNewHorizons.github.io/Horizon-QA/javadoc/index.html){ .md-button }
[:octicons-mark-github-16: Repository](https://github.com/GTNewHorizons/Horizon-QA){ .md-button }

</div>

!!! abstract "At a glance"

    - Forge **1.7.10**, Java **8** bytecode, interactive by default with explicit `ci` and `off` modes.
    - Tests are plain `@GameTest` static methods on `@GameTestHolder` classes, discovered by ASM at server start.
    - Each test runs in an isolated cell on a dedicated void world; failures stay placed for in-game triage.
    - Output is a single `TEST-horizonqa.xml` suitable for any JUnit-aware CI.

## What it is for

Most GTNH regressions are not arithmetic bugs in a pure function. They are **machines forming when they should not**, **recipes running under broken maintenance**, **outputs routed to the wrong bus**, or **two mods quietly disagreeing about a tile entity**. Horizon-QA exercises those scenarios against the real server and reports them with enough context to act on the XML alone.

## Quick example: negative test

A common idiom is asserting every tick that something bad **does not** happen:

```java
@GameTest(template = "ebf_no_coils", timeoutTicks = 60) // (1)!
public static void doesNotFormWithoutCoils(GameTestHelper helper) {
    Multiblock ebf = helper.gtnh().multiblock(at(1, 0, 0));
    helper.onEachTick(() -> // (2)!
        helper.assertFalse(ebf.isFormed(), "EBF formed without coils"));
    helper.succeedAtTimeout(); // (3)!
}
```

1.  `template` resolves to `<holder>:ebf_no_coils`, a structure deliberately missing its heating coils.
2.  `onEachTick` re-runs the closure every test tick; a transient `true` fails immediately, on that tick.
3.  `succeedAtTimeout` passes only if the full window elapses without any assertion firing.

See [Negative assertions](guide/negative-tests.md) for the rest of the pattern.

## Capabilities

<div class="grid cards" markdown>

-   :material-test-tube:{ .lg .middle } **Functional E2E**

    ---

    Boots a real dedicated server and drives real tile entities. GT logic is never mocked.

-   :material-cube-outline:{ .lg .middle } **Structure templates**

    ---

    Export multiblocks in-game with the wand; load `namespace:path` JSON + NBT from your mod jar.

-   :material-lightning-bolt:{ .lg .middle } **GTNH helpers**

    ---

    EU supply, maintenance, time-warp, role-based hatch addressing, scoped synthetic recipes.

-   :material-timeline-text:{ .lg .middle } **Typed event log**

    ---

    Ordered, tick-stamped events in JUnit `<system-out>`. Diagnose CI failures without relaunching the game.

-   :material-eye:{ .lg .middle } **Visual debugging**

    ---

    Beacons, ghost-block diffs, overlay text, and wand-based selection in the dev client.

-   :material-pipe:{ .lg .middle } **CI-ready**

    ---

    Single `TEST-horizonqa.xml` with failures, warnings, and optional event traces per case.

</div>

## Where to go next

[Getting started](getting-started/index.md) walks new authors from "I just cloned the mod" to "my first passing test." [Guides](guide/index.md) cover task-oriented topics: structures, sequences, the GTNH multiblock façade, CI wiring, and [failure triage](guide/debugging.md). [Reference](reference/index.md) collects the annotations, commands, JVM flags, and event catalog you will look up most often.

!!! info "Legal note"

    Horizon-QA is a **clean-room** implementation inspired by modern GameTest ergonomics, not a port of Mojang source. Do not submit decompiled modern Minecraft code in pull requests; see [Clean-room policy](contributing/legal.md).
