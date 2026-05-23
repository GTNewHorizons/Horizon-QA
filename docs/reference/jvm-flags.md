---
title: JVM & system properties
description: Properties that gate Horizon-QA activation and event recording.
tags:
  - reference
  - jvm
---

# JVM & system properties

Horizon-QA reads two system properties at startup. Both are defined in `GameTestJvmFlags`.

## `gtnh.horizonqa`

| Property          | Values            | Default |
|-------------------|-------------------|---------|
| `gtnh.horizonqa`  | `true` / `false`  | `false` |

**Required** to activate Horizon-QA on the server:

```text
-Dgtnh.horizonqa=true
```

When `false`, the mod loads but its mixins and the test runner do not take over the dedicated world flow — there is no measurable cost.

## `horizonqa.events`

| Property           | Values                            | Default |
|--------------------|-----------------------------------|---------|
| `horizonqa.events` | `on` / `off` (case-insensitive)   | `on`    |

Controls the event recorder behind `EventLog`:

`on`
:   Record typed events. Each `<testcase>` in the JUnit XML may include the event log under `<system-out>`.

`off`
:   Recording is a no-op. Emit sites use `Supplier` instances that are never invoked — no payload allocation work.

```text
-Dhorizonqa.events=off
```

!!! warning "`off` removes your main failure diagnostic"

    Disable event recording only for performance micro-benchmarks, not for normal CI. The event log is the canonical source of "what happened" on a failing test.

## Gradle example

```kotlin
tasks.named<JavaExec>("runServer") {
    jvmArgs(
        "-Dgtnh.horizonqa=true",
        // "-Dhorizonqa.events=off",  // micro-benchmarks only
    )
}
```

## Reports

JUnit output path is fixed, relative to the server process working directory:

```text
TEST-horizonqa.xml
```

No system property overrides this path today; archive the file directly in CI after the batch completes.
