---
title: JVM properties
description: Horizon-QA server JVM properties for interactive authoring, CI execution, reports, selectors, and event recording.
---

# JVM properties

Horizon-QA reads Java system properties from the Minecraft **server** JVM. With RetroFuturaGradle `runServer`, pass them through RFG's `--mcJvmArgs` option:

```text
./gradlew runServer --mcJvmArgs=-Dhorizonqa.mode=ci --mcJvmArgs="-Dhorizonqa.reportDir=${PWD}/build/horizonqa"
```

Repeat `--mcJvmArgs` for each JVM argument. RFG exposes it as a Gradle collection option and does not split a quoted value on spaces.

Passing `-Dhorizonqa.mode=ci` directly to Gradle sets the property on the Gradle daemon, where the server never sees it.

## `horizonqa.mode`

| Property         | Values                         | Default       |
|------------------|--------------------------------|---------------|
| `horizonqa.mode` | `off` / `interactive` / `ci`   | `interactive` |

Controls Horizon-QA runtime behavior.

`interactive`
:   Enables `/horizonqa` commands, discovery, and client-side test visuals for local authoring. This is the default when the property is unset.

`ci`
:   Automation preset: non-interactive server behavior, automatic selected-test execution, report writing, and server exit. Defaults to the void test world.

`off`
:   Loads the mod while leaving commands, discovery, runner behavior, and test visuals inert.

Modes are presets. Runtime behavior is resolved from the mode defaults plus the override properties below, so you do not need a new mode for every combination of world, autorun, shutdown, and placement policy.

Examples:

```text
./gradlew runServer --mcJvmArgs="-Dhorizonqa.mode=interactive"
./gradlew runServer --mcJvmArgs="-Dhorizonqa.mode=ci"
./gradlew runServer --mcJvmArgs=-Dhorizonqa.mode=ci --mcJvmArgs=-Dhorizonqa.autoRun=false
./gradlew runServer --mcJvmArgs=-Dhorizonqa.mode=ci --mcJvmArgs=-Dhorizonqa.autoRun=false --mcJvmArgs=-Dhorizonqa.world=normal --mcJvmArgs=-Dhorizonqa.gridOrigin=0,128,0
./gradlew runServer --mcJvmArgs="-Dhorizonqa.mode=off"
```

## Runtime behavior overrides

| Property               | Values             | Default                                                   |
|------------------------|--------------------|-----------------------------------------------------------|
| `horizonqa.world`      | `void` / `normal`  | `void` in `ci`, otherwise `normal`                        |
| `horizonqa.autoRun`    | `true` / `false`   | `true` in `ci`, otherwise `false`                         |
| `horizonqa.stopServer` | `true` / `false`   | `true` in `ci` when autorun is enabled, otherwise `false` |
| `horizonqa.turbo`      | integer `1`–`100`  | `1`                                                       |
| `horizonqa.gridOrigin` | `x,y,z`            | `0,64,0`                                                  |

`horizonqa.world`
:   `void` forces Horizon-QA's dedicated void world type for dimension 0. `normal` leaves the server's configured or existing world type alone.

`horizonqa.autoRun`
:   Runs the selected tests automatically after server startup. When this is `false`, `/horizonqa run`, `/horizonqa runall`, and `/horizonqa runfailed` still start Reported Runs in `ci` mode. If enabled in interactive mode, startup uses the Reported Run lifecycle; interactive launch, relaunch, and clear commands are rejected until it finishes.

`horizonqa.stopServer`
:   Requests process exit after a Reported Run finishes. When `false`, the server remains up after the result is written.

`horizonqa.turbo`
:   Runs up to this many server ticks for each normal 20 TPS accumulator slot while a Reported Run is active in `ci` mode. `1` leaves the normal tick rate unchanged. Turbo stops when the run finishes; interactive test sessions and server time outside that window keep the normal cadence. Tick-scheduled player/world autosaves and vanilla's `Can't keep up!` warning are suppressed only while turbo is active.

    Every tick body still runs in order. Code that mixes ticks with wall-clock time can therefore behave differently under turbo, and a configured multiplier can still be limited by the CPU cost of a tick.

`horizonqa.gridOrigin`
:   Sets the absolute world coordinate where the test grid starts. Use `x,y,z`; `y` must be between `0` and `255`, and the full template height must still fit below the build limit. This affects both automatic and manual test placement.

Useful combinations:

```text
# CI reports, normal terrain, exit when done
./gradlew runServer --mcJvmArgs=-Dhorizonqa.mode=ci --mcJvmArgs=-Dhorizonqa.world=normal

# CI-style autorun, normal terrain, keep the server available afterward
./gradlew runServer --mcJvmArgs=-Dhorizonqa.mode=ci --mcJvmArgs=-Dhorizonqa.world=normal --mcJvmArgs=-Dhorizonqa.stopServer=false

# Manual Reported Runs at Y=128 in the configured world
./gradlew runServer --mcJvmArgs=-Dhorizonqa.mode=ci --mcJvmArgs=-Dhorizonqa.autoRun=false --mcJvmArgs=-Dhorizonqa.world=normal --mcJvmArgs=-Dhorizonqa.gridOrigin=0,128,0

# Manual Reported Runs with CI overrides
./gradlew runServer --mcJvmArgs=-Dhorizonqa.mode=ci --mcJvmArgs=-Dhorizonqa.autoRun=false --mcJvmArgs="-Dhorizonqa.reportDir=${PWD}/build/horizonqa"

# Ten times the normal tick target during the Reported Run
./gradlew runServer --mcJvmArgs=-Dhorizonqa.mode=ci --mcJvmArgs=-Dhorizonqa.turbo=10 --mcJvmArgs="-Dhorizonqa.reportDir=${PWD}/build/horizonqa"
```

## `horizonqa.tests`

| Property          | Values                    | Default         |
|-------------------|---------------------------|-----------------|
| `horizonqa.tests` | comma-separated selectors | all valid tests |

Limits automatic execution to selected tests. Manual reported batches use the command arguments instead and ignore this property.

Selector grammar:

```text
selectors := selector ("," selector)*
selector  := unqualified-selector | test-id-prefix
unqualified-selector := token-without-colon
test-id-prefix := namespace ":" class-or-method-prefix
```

Rules:

- unset or empty selects all valid tests,
- an unqualified selector matches either a namespace or a holder's simple or fully qualified class name,
- a test-ID prefix contains `:` and matches every valid test ID that starts with the selector,
- a full test ID remains valid and normally selects one test; use a shorter method-name prefix to select a subsystem,
- tokens are trimmed around commas,
- empty tokens such as `a,,b` are invalid,
- `*` is not supported,
- test-ID prefixes must contain exactly one `:`.

Examples:

```text
-Dhorizonqa.tests=horizonqaexamples
-Dhorizonqa.tests=BasicTests
-Dhorizonqa.tests=horizonqaexamples:BasicTests
-Dhorizonqa.tests=horizonqaexamples:BasicTests.simple
-Dhorizonqa.tests=horizonqaexamples:BasicTests.simplePass
-Dhorizonqa.tests=horizonqaexamples,othermod:SmokeTests.boots
```

For automatic execution, invalid selector syntax is a fatal CI configuration issue and exits `2`. Selectors that are syntactically valid but match no valid tests are reported as CI infrastructure issues; any other matched tests still run.

## `horizonqa.allowNoTests`

| Property                 | Values           | Default |
|--------------------------|------------------|---------|
| `horizonqa.allowNoTests` | `true` / `false` | `false` |

Allows an automatic CI run with no selected valid tests to pass. This has no effect on manual reported batches, which select tests from the command arguments. For automatic execution, this only applies when the empty selection is otherwise clean; selector/configuration infrastructure issues still fail CI.

```text
-Dhorizonqa.allowNoTests=true
```

## `horizonqa.allowLegacyNumericItemIds`

| Property                               | Values           | Default |
|----------------------------------------|------------------|---------|
| `horizonqa.allowLegacyNumericItemIds` | `true` / `false` | `false` |

Temporarily allows interactive `/horizonqa load` to interpret numeric ItemStack IDs in a version 1 template using the current item registry. Parsing is strict: only lowercase `true` and `false` are accepted.

This property is a migration tool, not a compatibility mode. It affects only interactive `/horizonqa load`; automatic CI, reported batches, and interactive test execution continue to reject legacy numeric ItemStack IDs even when it is `true`.

Run it only in the original environment whose numeric registry produced the template, then use `/horizonqa export` to create a portable version 2 template and remove the property:

```text
./gradlew runServer --mcJvmArgs="-Dhorizonqa.allowLegacyNumericItemIds=true"
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

`horizonqa.reportFile` wins over `horizonqa.reportDir` for the JUnit XML path. When `horizonqa.reportDir` is set and `horizonqa.statusFile` is not set, status JSON defaults to `horizonqa-result.json` in that same directory.

Relative paths resolve from the Minecraft server process working directory. In GTNH Gradle projects this is normally `run/server`, not the repository root. Prefer an absolute path for CI artifacts.

Recommended CI and manual-report forms:

```text
./gradlew runServer --mcJvmArgs=-Dhorizonqa.mode=ci --mcJvmArgs="-Dhorizonqa.reportDir=${PWD}/build/horizonqa"
./gradlew runServer --mcJvmArgs=-Dhorizonqa.mode=ci --mcJvmArgs=-Dhorizonqa.autoRun=false --mcJvmArgs="-Dhorizonqa.reportDir=${PWD}/build/horizonqa"
```

## Status JSON

The status JSON is a concise machine-readable summary. Schema version `3` contains:

| Top-level field | Meaning                                                                        |
|-----------------|--------------------------------------------------------------------------------|
| `schemaVersion` | Integer schema version, currently `3`                                          |
| `status`        | `passed`, `failed`, or `error`                                                 |
| `exitCode`      | Process exit code Horizon-QA requests                                          |
| `configuration` | Effective property values and defaults                                         |
| `counts`        | Aggregate selected, passed, failed, timeout, skipped, optional, issue, and JUnit counts |
| `reports`       | JUnit and status report paths                                                  |
| `issues`        | Infrastructure/configuration/selection/reporting issues                        |
| `tests`         | Per-test status, optional report output, and optional failure details           |

Issue entries contain `id`, `kind`, `source`, `name`, `message`, `fatalInCi`, and optional `details` / `stackTrace`.
Test entries contain `id`, `classname`, `name`, `status`, `required`, `ticks`, `timeSeconds`, optional `parameters`
for a parameterized case, optional `output` for parameter, warning, and event-log lines, optional
`blockedByIssueId`, optional `failure`, or `skipReason` / `skipType` when `status` is `skipped`. The `output` field
is omitted when empty.

## Exit codes

| Code | Status   | Meaning                                                                                                                |
|------|----------|------------------------------------------------------------------------------------------------------------------------|
| `0`  | `passed` | No required test failures and no infrastructure errors                                                                 |
| `1`  | `failed` | At least one required test failed or timed out                                                                         |
| `2`  | `error`  | Infrastructure, configuration, discovery-selection, template, cleanup, execution-abort, report-path, reporting, or incomplete-run error |

Optional failures and intentional skips do not change the process exit code by themselves. They are counted in status JSON and represented as skipped in JUnit XML.

!!! warning "Use lowercase property values"

    CI property parsing is strict. Use `ci`, `interactive`, `void`, `normal`, `true`, `false`, `on`, and `off` exactly as documented.
