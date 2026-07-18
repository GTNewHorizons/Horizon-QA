---
title: Runtime lifecycle
description: How Horizon-QA discovers, prepares, runs, cleans, classifies, and reports a test.
---

# Runtime lifecycle

Every Horizon-QA test follows the same broad lifecycle. Interactive and reported execution differ in orchestration, but they share discovery, test instances, tick phases, completion, and cleanup.

## 1. Discover and validate

At server startup, Forge ASM metadata identifies classes annotated with `@GameTestHolder`. Horizon-QA loads those holder classes and validates their test methods and batch hooks through reflection.

A runnable test method must be `public static`, return `void`, and accept exactly one `GameTestHelper`. Discovery also validates holder namespaces, template prefixes, timeout values, rotations, batch names, and duplicate test IDs.

Invalid definitions are excluded from the runnable set and logged with a reason. Reported execution can carry discovery and selection issues into its machine-readable result instead of silently ignoring them.

## 2. Select tests

The selection source depends on how the framework is running:

- Interactive commands choose an exact test, a namespace, or remembered failures and launch the resulting tests directly.
- Automatic execution applies `horizonqa.tests` selectors after server startup.
- Manually reported commands use their command arguments and ignore the automatic selector property.

Reported execution groups selected tests by `batch` before preparation. See [Execution model](execution-model.md) for ordering, concurrency, and hook behavior.

## 3. Prepare the fixture

For each test, Horizon-QA allocates a grid cell, forces the required chunks, clears the cell and its margin, and loads the declared structure template from the classpath.

The structure layer:

- resolves a qualified template name,
- loads and validates JSON plus optional structure data,
- rotates blocks, tile entities, entities, and labels,
- restores tile and entity NBT,
- exposes named labels through `helper.pos(...)`.

Template-load failure behavior differs between reported and interactive execution. See [Fixtures, coordinates, and isolation](fixtures-and-isolation.md#interactive-template-loading) for the current contract.

## 4. Start and run

Once preparation succeeds, Horizon-QA creates a test instance and calls the test method on the server thread. The method may:

- complete synchronously with `helper.succeed()`,
- register tick callbacks,
- install a `succeedWhen` predicate,
- build a `GameTestSequence`,
- request success at the timeout boundary,
- call GregTech helpers and synchronous time-warp operations.

The instance then advances through normal server START and END phases until it passes, fails, times out, or becomes an infrastructure error. Detailed timing behavior lives in [Execution model](execution-model.md#normal-test-ticks).

## 5. Complete and clean

Every started test runs callbacks registered through `helper.afterTest(...)` on each completion path:

- pass,
- assertion failure,
- unexpected exception,
- timeout,
- in-test infrastructure error.

Framework features use the same mechanism for owned resources such as temporary recipe removal. Cleanup callbacks continue running even if one callback fails, so later resources still get a chance to release.

A cleanup failure changes the final test status to an infrastructure error. The built-in isolation scan also runs as cleanup. Its exact spatial checks and its limits are covered in [Fixtures, coordinates, and isolation](fixtures-and-isolation.md#the-built-in-isolation-scan).

## 6. Classify the result

Horizon-QA separates test behavior from infrastructure:

| Result | Meaning |
|---|---|
| Passed | The test reached its declared success path and cleanup succeeded |
| Failed | An assertion or other test-body failure occurred |
| Timed out | The test reached `timeoutTicks` without succeeding |
| Error | Setup, label resolution, cleanup, configuration, selection, reporting, or another infrastructure concern failed |

Framework block and tile assertions can attach a specific world position to `GameTestAssertException`. Interactive overlays use that context to highlight the relevant cell or block.

## 7. Expose the outcome

Interactive execution keeps placed cells and visual status available for investigation. A connected development client can render beacons, labels, highlight boxes, and block differences.

Automatic and manually reported runs write:

- `TEST-horizonqa.xml` for JUnit-aware CI systems.
- `horizonqa-result.json` for run status, configuration, counts, issues, and per-test results.

When event recording is enabled, each reported test case can include an ordered event trace in JUnit `<system-out>`. The trace explains what happened before the final status was reached.

See [Test event log](../reference/events.md) for emitted records and [Debugging failed tests](../guide/debugging.md) for the investigation workflow.

## Component map

| Layer | Responsibility |
|---|---|
| Author API | `@GameTest`, `GameTestHelper`, sequences, assertions, and GTNH helpers |
| Discovery | Reads Forge metadata, loads holders, validates signatures, and rejects duplicate IDs |
| Structures | Loads classpath templates, rotates fixtures, restores data, and resolves labels |
| Execution | Allocates cells, drives test instances, enforces timeouts, and runs reported batches |
| Observability | Records typed events, renders overlays, and writes JUnit XML plus status JSON |
