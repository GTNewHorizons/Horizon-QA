---
title: CI & JUnit reports
description: TEST-horizonqa.xml format, event traces in CI, and a sketch of Gradle / GitHub Actions wiring.
tags:
  - guides
  - ci
---

# CI & JUnit reports

Batch execution writes **`TEST-horizonqa.xml`** in the server working directory when `/horizonqa runall` (or a headless batch entrypoint) completes. The format is the standard JUnit XML schema, so any JUnit-aware result publisher will pick it up unchanged.

## Report contents

```xml
<testsuite name="horizonqa" tests="…" failures="…" errors="…" skipped="…" …>
  <testcase name="methodName" classname="namespace:ClassName" time="…">
    <!-- failure / error / system-out elements as appropriate -->
  </testcase>
</testsuite>
```

| Field        | Meaning                                                    |
|--------------|------------------------------------------------------------|
| `classname`  | Test id prefix (`namespace:ClassName`)                     |
| `name`       | Method name                                                |
| `time`       | Duration in seconds (`testTicks / 20`)                     |

Failed tests include stack traces. Passing tests with warnings or recorded events also emit `<system-out>` blocks.

## Event log in CI

When event recording is enabled (default), each `<testcase>` can include the ordered `[t=NNN] [category] summary` log. The same log is mirrored to the server console (last 20 lines on failure) so a Jenkins console snippet is enough to triage most recipe, EU, and maintenance failures without a local rerun.

Disable for perf-sensitive jobs only — you lose the main failure diagnostic:

```text
-Dhorizonqa.events=off
```

Full catalog: [Test event log](../reference/events.md).

## CI pipeline shape

```mermaid
flowchart LR
    A[Start dedicated server<br/>with -Dgtnh.horizonqa=true] --> B[Trigger batch<br/>command block / RCON / entrypoint]
    B --> C[Server writes<br/>TEST-horizonqa.xml]
    C --> D[Archive as<br/>build artifact]
    D --> E[Publish via JUnit-aware<br/>action / plugin]
```

In words:

1. Start a dedicated server with `-Dgtnh.horizonqa=true`.
2. Trigger a batch — typically via a command block, RCON, or a mod-specific CI entrypoint that calls `/horizonqa runall` once the world is ready.
3. Archive `TEST-horizonqa.xml` as a build artifact.
4. Point Jenkins / GitHub Actions `publish-unit-test-result` (or your equivalent) at the XML.

This repository's docs site build ([`.github/workflows/publish.yml`](https://github.com/GTNewHorizons/Horizon-QA/blob/master/.github/workflows/publish.yml)) runs `mkdocs build` and `./gradlew :javadoc` on push to `master`. Consumer mods wire their own game-test CI on top of the same shape.

## `required = false` and skipped semantics

Optional tests that fail may be reported without failing the suite aggregate. Reserve this for genuinely quarantined cases — required tests should gate merges, and a permanent failing-optional test is a maintenance leak.

## Local iteration loop

```text
edit test → runServer → /horizonqa run <id>   → inspect overlay + XML
         → /horizonqa runfailed              → re-run only the failures
```

## Publishing these docs

Documentation is published to GitHub Pages from `master`. To preview locally:

```bash
pip install -r requirements.txt
mkdocs serve
```

Open `http://127.0.0.1:8000` for live-reload preview.
