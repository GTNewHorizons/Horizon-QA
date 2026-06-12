---
title: Guides
description: Task-oriented documentation for authoring and maintaining Horizon-QA tests.
tags:
  - guides
---

# Guides

Task-oriented documentation for authors writing and maintaining GameTests. Each page is a self-contained recipe; read them in any order.

<div class="grid cards" markdown>

-   :material-pencil-outline:{ .lg .middle } **[Writing tests](writing-tests.md)**

    ---

    Holders, ids, batches, cleanup, the `required` flag, rotations.

-   :material-cube-outline:{ .lg .middle } **[Structure templates](structures.md)**

    ---

    Exporting with the wand, the JSON format, placement, rotation safety.

-   :material-cog-outline:{ .lg .middle } **[GTNH multiblock API](gtnh-api.md)**

    ---

    `Multiblock`, role-based hatches, time-warp, EU supply, scoped recipes.

-   :material-shield-alert-outline:{ .lg .middle } **[Negative assertions](negative-tests.md)**

    ---

    `onEachTick`, `succeedAtTimeout`, asserting an invariant over a window.

-   :material-timer-outline:{ .lg .middle } **[Sequences & timing](sequences.md)**

    ---

    `GameTestSequence` for ordered steps, delays, and bounded waits.

-   :material-server-outline:{ .lg .middle } **[CI & JUnit reports](ci.md)**

    ---

    XML output, event traces in CI, Gradle and GitHub Actions wiring.

-   :material-bug-outline:{ .lg .middle } **[Debugging failed tests](debugging.md)**

    ---

    Triage from the XML and event trace, reproduce in-game, iterate.

</div>

Lookup-style material (annotations, commands, JVM flags, event catalog) lives under [Reference](../reference/index.md).
