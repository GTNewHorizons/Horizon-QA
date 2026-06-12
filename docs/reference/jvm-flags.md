---
title: JVM & system properties
description: Horizon-QA server JVM properties for interactive authoring, CI execution, reports, selectors, and event recording.
tags:
  - reference
  - jvm
---

# JVM & system properties

Horizon-QA reads Java system properties from the Minecraft **server** JVM. With Retrofuturagradle `runServer`, pass them through RFG's `--mcJvmArgs` option:

```text
./gradlew runServer --mcJvmArgs="-Dhorizonqa.mode=ci -Dhorizonqa.reportDir=build/horizonqa"
```

Passing `-Dhorizonqa.mode=ci` directly to Gradle sets the property on the Gradle daemon, where the server never sees it.

## `horizonqa.mode`

| Property         | Values                                  | Default       |
|------------------|-----------------------------------------|---------------|
| `horizonqa.mode` | `off` / `interactive` / `report` / `ci` | `interactive` |

Controls Horizon-QA runtime behavior.

`interactive`
:   Enables `/horizonqa` commands, discovery, and client-side test visuals for local authoring. This is the default when the property is unset.

`report`
:   Enables the CI-style void test world plus manual `/horizonqa run`, `/horizonqa runall`, and `/horizonqa runfailed` batches that write JUnit XML and status JSON reports. The server does not auto-run tests at startup and does not exit after the batch completes.

`ci`
:   Enables deterministic headless execution: void world registration, CI-oriented server behavior, automatic selected-test execution, report writing, and server exit.

`off`
:   Loads the mod while leaving commands, discovery, runner behavior, and test visuals inert.

Examples:

```text
./gradlew runServer --mcJvmArgs="-Dhorizonqa.mode=interactive"
./gradlew runServer --mcJvmArgs="-Dhorizonqa.mode=report -Dhorizonqa.reportDir=build/horizonqa"
./gradlew runServer --mcJvmArgs="-Dhorizonqa.mode=ci"
./gradlew runServer --mcJvmArgs="-Dhorizonqa.mode=off"
```

## `horizonqa.tests`

| Property          | Values                    | Default         |
|-------------------|---------------------------|-----------------|
| `horizonqa.tests` | comma-separated selectors | all valid tests |

Limits automatic CI execution to selected tests. `report` mode is manual and uses the command arguments instead.

Selector grammar:

```text
selectors := selector ("," selector)*
selector  := namespace | exact-test-id
namespace := token-without-colon
exact-test-id := namespace ":" class-and-method
```

Rules:

- unset or empty selects all valid tests,
- `namespace` selects every valid test whose id starts with `namespace:`,
- `namespace:Class.method` selects one exact test id,
- tokens are trimmed around commas,
- empty tokens such as `a,,b` are invalid,
- `*` is not supported,
- exact test ids must contain exactly one `:`.

Examples:

```text
-Dhorizonqa.tests=horizonqaexamples
-Dhorizonqa.tests=horizonqaexamples:BasicTests.simplePass
-Dhorizonqa.tests=horizonqaexamples,othermod:SmokeTests.boots
```

Invalid selector syntax is a fatal CI configuration issue and exits `2`. Selectors that are syntactically valid but match no valid tests are reported as CI infrastructure issues; any other matched tests still run.

## `horizonqa.allowNoTests`

| Property                 | Values           | Default |
|--------------------------|------------------|---------|
| `horizonqa.allowNoTests` | `true` / `false` | `false` |

Allows a CI run with no selected valid tests to pass. This only applies when the empty selection is otherwise clean; selector/configuration infrastructure issues still fail CI.

```text
-Dhorizonqa.allowNoTests=true
```

## `horizonqa.events`

| Property           | Values       | Default |
|--------------------|--------------|---------|
| `horizonqa.events` | `on` / `off` | `on`    |

Controls the event recorder behind `EventLog`.

`on`
:   Records typed events. Each JUnit `<testcase>` may include the event log under `<system-out>`.

`off`
:   Disables recording. Emit sites use suppliers that are never invoked, so payload allocation work is skipped.

```text
-Dhorizonqa.events=off
```

Disable event recording only for performance investigations; it is the primary CI failure diagnostic.

## Report paths

Default outputs:

```text
TEST-horizonqa.xml
horizonqa-result.json
```

| Property               | Meaning                                                   |
|------------------------|-----------------------------------------------------------|
| `horizonqa.reportFile` | Exact JUnit XML output path                               |
| `horizonqa.reportDir`  | Directory containing `TEST-horizonqa.xml`                 |
| `horizonqa.statusFile` | Exact status JSON output path                             |

`horizonqa.reportFile` wins over `horizonqa.reportDir` for the JUnit XML path. When `horizonqa.reportDir` is set and `horizonqa.statusFile` is not set, status JSON defaults to `horizonqa-result.json` in that same directory. Relative paths resolve from the server process working directory.

Recommended CI and manual-report forms:

```text
./gradlew runServer --mcJvmArgs="-Dhorizonqa.mode=ci -Dhorizonqa.reportDir=build/horizonqa"
./gradlew runServer --mcJvmArgs="-Dhorizonqa.mode=report -Dhorizonqa.reportDir=build/horizonqa"
```

## Status JSON

The status JSON is a concise machine-readable summary. Schema version `1` contains:

| Top-level field | Meaning                                                                        |
|-----------------|--------------------------------------------------------------------------------|
| `schemaVersion` | Integer schema version, currently `1`                                          |
| `status`        | `passed`, `failed`, or `error`                                                 |
| `exitCode`      | Process exit code Horizon-QA requests                                          |
| `configuration` | Effective property values and defaults                                         |
| `counts`        | Aggregate selected, passed, failed, timeout, optional, issue, and JUnit counts |
| `reports`       | JUnit and status report paths                                                  |
| `issues`        | Infrastructure/configuration/selection/reporting issues                        |
| `tests`         | Per-test status and optional failure details                                   |

Issue entries contain `id`, `kind`, `source`, `name`, `message`, `fatalInCi`, and optional `details` / `stackTrace`. Test entries contain `id`, `classname`, `name`, `status`, `required`, `ticks`, `timeSeconds`, optional `blockedByIssueId`, and optional `failure`.

## Exit codes

| Code | Status   | Meaning                                                                                                                |
|------|----------|------------------------------------------------------------------------------------------------------------------------|
| `0`  | `passed` | No required test failures and no infrastructure errors                                                                 |
| `1`  | `failed` | At least one required test failed or timed out                                                                         |
| `2`  | `error`  | Infrastructure, configuration, discovery-selection, template, cleanup, report-path, reporting, or incomplete-run error |

Optional failures do not change the process exit code by themselves. They are counted in status JSON and represented as skipped in JUnit XML.

!!! warning "Use lowercase property values"

    CI property parsing is strict. Use `ci`, `report`, `interactive`, `true`, `false`, `on`, and `off` exactly as documented.
