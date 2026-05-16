---
title: JVM & system properties
description: Properties that gate Horizon-QA activation and event recording.
tags:
  - reference
  - jvm
---

# JVM & system properties

Horizon-QA reads two system properties at startup. Both are defined in `GameTestJvmFlags`.

## `gtnh.gametest`

| Property         | Values            | Default |
|------------------|-------------------|---------|
| `gtnh.gametest`  | `true` / `false`  | `false` |

**Required** to activate Horizon-QA on the server:

```text
-Dgtnh.gametest=true
```

When `false`, the mod loads but its mixins and the test runner do not take over the dedicated world flow — there is no measurable cost.

## `gametest.events`

| Property          | Values                            | Default |
|-------------------|-----------------------------------|---------|
| `gametest.events` | `on` / `off` (case-insensitive)   | `on`    |

Controls `TestEventRecorder`:

`on`
:   Record typed events. Each `<testcase>` in the JUnit XML may include the event log under `<system-out>`.

`off`
:   Recording is a no-op. Emit sites use `Supplier` instances that are never invoked — no payload allocation work.

```text
-Dgametest.events=off
```

!!! warning "`off` removes your main failure diagnostic"

    Disable event recording only for performance micro-benchmarks, not for normal CI. The event log is the canonical source of "what happened" on a failing test.

## Gradle example

```kotlin
tasks.named<JavaExec>("runServer") {
    jvmArgs(
        "-Dgtnh.gametest=true",
        // "-Dgametest.events=off",  // micro-benchmarks only
    )
}
```

## Reports

JUnit output path is fixed, relative to the server process working directory:

```text
TEST-gametest.xml
```

No system property overrides this path today; archive the file directly in CI after the batch completes.
