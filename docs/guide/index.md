---
title: Guides
description: Task-oriented documentation for authoring and maintaining Horizon-QA tests.
---

# Guides

Use these guides when you are building, running, or investigating Horizon-QA tests. Each page focuses on a specific task and can be read independently.

## Author tests

[Writing tests](writing-tests.md)
: Define holder classes, test IDs, batches, cleanup, rotations, and required tests.

[Structure templates](structures.md)
: Export fixtures, understand the template format, place structures, and work safely with rotations.

[GTNH multiblock API](gtnh-api.md)
: Drive GregTech multiblocks with typed controllers, time-warp, EU supply, maintenance controls, and temporary recipes.

[Negative assertions](negative-tests.md)
: Observe a condition over time and prove that an invalid state never occurs.

[Sequences and timing](sequences.md)
: Coordinate ordered steps, delays, tick phases, polling, and bounded waits with `GameTestSequence`.

## Run and diagnose

[CI and JUnit reports](ci.md)
: Run selected tests in automation and publish JUnit XML, status JSON, and event traces.

[Setup troubleshooting](troubleshooting.md)
: Resolve missing commands or tests, template and label errors, absent reports, and unsupported controllers.

[Debugging failed tests](debugging.md)
: Read reports and event traces, reproduce failures in-game, and inspect the affected test cell.

Lookup-style material (annotations, commands, JVM flags, event catalog) lives under [Reference](../reference/index.md).
