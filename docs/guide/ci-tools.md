---
title: CI tooling
description: Launch client tests with an existing Gradle or packaged runtime and verify their reports.
---

# CI tooling

Use the optional `hqa-tools.pyz` archive to launch client tests and verify reports.
HQA runs the tests; HeadlessNH navigates menus and creates the singleplayer world; the tools own
the display, process deadline, and verification. An existing CI launcher can own those steps instead.

Verification needs Python 3.9+. Automated client launches additionally need Linux, Xvfb, xdotool,
an AWT-capable Java runtime, and HQA, test JARs, and HeadlessNH in the client.

## Get the tools

Build locally with `./gradlew hqaTools`; the output is `build/libs/horizonqa-<version>-tools.pyz`.
For CI, pin a release providing the Maven artifact
`com.github.GTNewHorizons:Horizon-QA:<version>:tools@pyz`.
The archive uses Python's standard library and needs no adjacent scripts or source checkout.

```bash
python3 hqa-tools.pyz --version
python3 hqa-tools.pyz run-client --help
python3 hqa-tools.pyz verify --help
```

## Development shortcut

In Horizon-QA, `./gradlew hqaClientTest` builds the tools and runs the examples on a private display.
To watch them on your desktop, use the [visible run command](client-tests.md#visible-local-runs).

| Gradle property | Default |
|---|---|
| `hqaTests` | `horizonqaexamples:ClientInteractionTests` |
| `hqaClientTask` | `:examples:runClient`; select a supported `runClientXX` task for modern Java |
| `hqaReportDir` | New directory under `build/horizonqa/`; an explicit path must not exist |
| `hqaTimeout` | `900` seconds for the whole launch |

In a consumer repository, target its existing RFG client task:

```bash
python3 hqa-tools.pyz run-client --cwd "$PWD" --gradle-task runClient \
  --tests mymod:ChatTests --report-dir "$PWD/build/client-run"
```

The tool passes each Minecraft JVM property through its own `--mcJvmArgs` and enables
`-PhqaHeadlessNH=true`. Include HeadlessNH conditionally in the consumer's `dependencies.gradle`:

```groovy
if (providers.gradleProperty('hqaHeadlessNH').getOrElse('false').toBoolean()) {
    runtimeOnlyNonPublishable('net.sxmaa.headlessnh:HeadlessNH:1.1.1:dev')
}
```

Each run creates a fresh game/report directory and verifies matching JSON/JUnit plus process exit.
Gradle reports failed tasks as exit `1`; use the reports and `launch-result.json` for HQA's precise
`0` / `1` / `2` outcome.

## Existing packaged launchers

1. Install reobfuscated HQA and test JARs plus HeadlessNH in an isolated pack without `saves`.
2. Use the pack's bootstrapper to prepare libraries and natives. Modern Java needs the pack's
   supported lwjgl3ify configuration.
3. Write its prepared Java command to `launch.argv`: one literal argument per line, starting with
   the absolute Java executable. Its game directory must match `--cwd`.
4. Choose a report directory that does not exist, then run:

```bash
python3 hqa-tools.pyz run-client --cwd /path/to/isolated-pack/.minecraft \
  --argv-file /path/to/isolated-pack/launch.argv \
  --env-file /path/to/isolated-pack/launch.env \
  --tests mymod:ChatTests --report-dir /path/to/new-reports --timeout 900
```

`--env-file` is optional: one shell-style assignment per line, parsed without shell evaluation.
An explicit `--gameDir` must match `--cwd`. Use `--xvfb` / `--xdotool` to select installed binaries.
The tool creates a private X11 display, positions/focuses the window, holds HeadlessNH's completion
gate, bounds execution, and collects reports. Native input stays on that display, including on
Wayland hosts.

The default `--headless singleplayer` creates a world. `--headless existing` retains a launcher's
navigation settings while still holding the completion gate. Extra arguments after `--` go to Gradle
or, for packaged commands, the JVM; managed HQA/HeadlessNH settings cannot be overridden.

## Integrate with an existing launcher

If CI already provides the display and process supervision, pass these properties to the
**Minecraft client JVM** and use `verify` after it exits:

```text
-Dhorizonqa.mode=ci
-Dhorizonqa.client=true
-Dhorizonqa.world=normal
-Dhorizonqa.autoRun=true
-Dhorizonqa.stopServer=true
-Dhorizonqa.turbo=1
-Dhorizonqa.tests=mymod:ChatTests
-Dhorizonqa.reportDir=/absolute/path/to/new-client-reports
-Djava.awt.headless=false
```

For Gradle Minecraft tasks, pass every property through its own `--mcJvmArgs`.
Use one effective value per property in a prepared Java command. The launcher's contract is:

- Use a fresh game/report directory. Give dedicated-server and client suites separate report
  directories and verify both.
- Create/join singleplayer and give the window actual OS focus; on Linux position it at `(0, 0)`.
  HeadlessNH's `forcefocus` alone is insufficient. With HeadlessNH, set
  `-Dheadlessnh.gate.worldloaded=hqa-complete.gate` and keep that gate absent during testing.
- Await HQA reports **and process completion**, rather than a world-loaded marker. HQA allows
  600 seconds to reach singleplayer; an earlier dedicated-server phase in the same client consumes
  that startup budget.
- Bound the launch. On deadline, create `stop-client` in the report directory, collect an owned-JVM
  thread dump, allow a bounded cleanup grace, then terminate only the invocation's processes.
  Crashes, deadlines, forced termination, or inconsistent process exits mean infrastructure exit `2`.

Retain both report directories on failure, including JSON, JUnit, `artifacts/`, launcher/game logs,
and crash reports. `run-client` handles this contract for its own launch and records
`launch-result.json`, `launch.log`, and `xvfb.log`.

## Verify reports

Record the Unix start time **before** launching; verify after the reported run finishes:

```bash
started_at="$(date +%s)"
# Launch and await the reported run.
python3 hqa-tools.pyz verify /path/to/reports \
  --tests mymod:ChatTests --earliest "$started_at"
```

`--tests` checks the automatic run's selector; omit it for manually selected RCON batches.
Always supply `--earliest` in CI (its default is `0`). Missing, stale, malformed, incomplete,
contradictory, or empty success reports fail with exit `2`.

Both commands preserve HQA's outcomes: optional failures/timeouts are `0` and JUnit-skipped;
required failures/timeouts are `1`; infrastructure/incomplete/reporting errors are `2`.
Verification checks [schema-3 JSON](../reference/jvm-flags.md#status-json) against JUnit.
Your launcher must also check process completion; report verification alone cannot prove a clean exit.
