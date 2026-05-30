---
title: JVM & system properties
description: Properties that gate Horizon-QA activation and event recording.
tags:
  - reference
  - jvm
---

# JVM & system properties

Horizon-QA reads system properties from the Minecraft server JVM. With Retrofuturagradle `runServer`, pass these through `--mcJvmArgs`; passing them directly to Gradle sets them on the Gradle daemon instead.

## `horizonqa.mode`

| Property         | Values                       | Default       |
|------------------|------------------------------|---------------|
| `horizonqa.mode` | `off` / `interactive` / `ci` | `interactive` |

Controls Horizon-QA runtime behavior:

`off`
:   The mod loads, but commands, discovery, runner behavior, and test visuals remain inert.

`interactive`
:   Enables `/horizonqa` commands, discovery, and client-side test visuals for local authoring.

`ci`
:   Enables deterministic headless execution: void world, no network bind, no spawns/nether, automatic test run, report writing, and server exit.

No mode property is required for local test authoring because `interactive` is the default. You can still set it explicitly:

```text
-Dhorizonqa.mode=interactive
```

Use `ci` for automated runs:

```text
-Dhorizonqa.mode=ci
```

Use `off` to load the mod without Horizon-QA commands, discovery, runner behavior, or test visuals:

```text
-Dhorizonqa.mode=off
```

## `horizonqa.tests`

| Property          | Values                         | Default         |
|-------------------|--------------------------------|-----------------|
| `horizonqa.tests` | comma-separated selectors      | all valid tests |

Limits automatic CI execution to selected tests:

- unset or empty selects all valid tests,
- `namespace` selects every valid test whose id starts with `namespace:`,
- `namespace:Class.method` selects one exact test id.

Empty selector tokens are invalid, so `a,,b` aborts CI before tests run. The `*` wildcard is not supported; omit the property or set it to an empty value to run all valid tests.

Selectors that are syntactically valid but match no valid tests are reported as CI infrastructure issues. If at least one selector matches valid tests, those tests still run and the infrastructure issue is included in the final CI result.

When no valid tests are selected and `horizonqa.allowNoTests=false`, CI writes a diagnostic JUnit report and exits with code `2`.

```text
-Dhorizonqa.tests=horizonqaexamples
-Dhorizonqa.tests=horizonqaexamples:BasicTests.simplePass
```

## `horizonqa.allowNoTests`

| Property                 | Values           | Default |
|--------------------------|------------------|---------|
| `horizonqa.allowNoTests` | `true` / `false` | `false` |

Allows a CI run with no selected valid tests to pass. This only applies when there are no selector or discovery-selection infrastructure issues.

## `horizonqa.events`

| Property           | Values                            | Default |
|--------------------|-----------------------------------|---------|
| `horizonqa.events` | `on` / `off`                      | `on`    |

Controls the event recorder behind `EventLog`:

`on`
:   Record typed events. Each `<testcase>` in the JUnit XML may include the event log under `<system-out>`.

`off`
:   Recording is a no-op. Emit sites use `Supplier` instances that are never invoked, so payload allocation work is skipped.

```text
-Dhorizonqa.events=off
```

!!! warning "`off` removes your main failure diagnostic"

    Disable event recording only for performance micro-benchmarks, not for normal CI. The event log is the canonical source of "what happened" on a failing test.

## CI Gradle example

```kotlin
tasks.named<JavaExec>("runServer") {
    jvmArgs(
        "-Dhorizonqa.mode=ci",
        // "-Dhorizonqa.events=off",  // micro-benchmarks only
    )
}
```

## Reports

JUnit output defaults to the server process working directory:

```text
TEST-horizonqa.xml
```

Override the JUnit path with either an exact file or an output directory:

| Property               | Meaning                                                                           |
|------------------------|-----------------------------------------------------------------------------------|
| `horizonqa.reportFile` | Exact JUnit XML file path                                                         |
| `horizonqa.reportDir`  | Directory containing `TEST-horizonqa.xml`                                         |
| `horizonqa.statusFile` | Parsed now for CI validation; status JSON writing lands in a later reporting pass |

`horizonqa.reportFile` wins over `horizonqa.reportDir`. Relative paths resolve from the server process working directory.

!!! warning "Use lowercase property values"

    CI property parsing is strict. Use `ci`, `true`, `false`, `on`, and `off` exactly as documented.
