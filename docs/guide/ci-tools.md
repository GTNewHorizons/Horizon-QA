---
title: CI tooling
description: Launch client tests in Linux automation and verify fresh, complete HQA reports.
---

# CI tooling

Use the optional tools archive to launch client tests in Linux automation and verify HQA reports.
It launches Minecraft through a Gradle client task or a packaged launcher's prepared Java command.
Install HQA and your test JARs in the game's runtime.

Report verification requires Python 3.9+. Client supervision additionally requires Linux, Xvfb,
xdotool, and an AWT-capable Java runtime. HeadlessNH automates menus and world creation.

## One versioned artifact

`./gradlew hqaTools` produces `build/libs/horizonqa-<version>-tools.pyz`. The archive contains the client
supervisor, report verifier, Gradle isolation adapter, version, and license. It runs with Python 3.9+
and the standard library, without a source checkout, installed Python package, or adjacent helper files.
`assemble` builds it, and `check` runs its protocol and standalone archive tests.

The Maven artifact coordinate is `com.github.GTNewHorizons:Horizon-QA:<version>:tools@pyz`.
Use a release that includes the `tools` classifier and pin its version in CI. A shared workflow can
acquire that archive once per job and pass it to each launch or verification stage.

Use the same archive for both commands:

```bash
python3 hqa-tools.pyz --version
python3 hqa-tools.pyz run-client --help
python3 hqa-tools.pyz verify --help
```

`launch-result.json` records `toolsVersion` alongside the process outcome. Game results use HQA's
[schema-3 status JSON](../reference/jvm-flags.md#status-json) and JUnit XML.

The supervisor owns a private X11 display. After reading launch environment overrides, it sets
`DISPLAY`, `XDG_SESSION_TYPE=x11`, and `SDL_VIDEO_DRIVER=x11`, and removes `WAYLAND_DISPLAY`.
This keeps native input and the game window on the same display on Wayland hosts. Graphics-driver
or native-library startup failures produce infrastructure exit `2`; retain their logs and crash reports.

## Development shortcut

In the Horizon-QA repository, run the focused client smoke suite with:

```bash
./gradlew hqaClientTest
```

This builds the archive and delegates to its supervisor. Each invocation chooses a new directory
under `build/horizonqa/`, which the printed launch summary identifies. It does not overwrite prior
reports or a developer's saves. The task is intentionally separate from `build` because it boots
Minecraft and requires a display/input environment.

The supervisor sets `-PhqaHeadlessNH=true` for Gradle client launches. Horizon-QA's examples use that
property to include HeadlessNH only for automation; ordinary development launches omit it. To watch
the tests on your desktop and create a world manually, use the
[visible local run](client-tests.md#visible-local-runs).

Override only what the run needs:

```bash
./gradlew hqaClientTest \
  -PhqaTests=horizonqaexamples:ClientInteractionTests.bChestGameplay \
  -PhqaReportDir=/absolute/path/to/new-reports
```

| Gradle property | Default | Meaning |
|---|---|---|
| `hqaTests` | `horizonqaexamples:ClientInteractionTests` | HQA selector |
| `hqaClientTask` | `:examples:runClient` | RFG task, including supported modern `runClientXX` tasks |
| `hqaReportDir` | New directory under `build/horizonqa/` | Must not already exist |
| `hqaTimeout` | `900` | Whole client launch deadline in seconds |

These properties configure the shortcut. The supervisor passes every Minecraft `horizonqa.*` property
through its own `--mcJvmArgs`. Gradle uses its usual exit `1` for a failed task; the supervisor and
reports retain the precise HQA exit `0`, `1`, or `2`.

Consumer repositories can use their existing RFG task through the standalone tool:

```bash
python3 hqa-tools.pyz run-client --cwd "$PWD" --gradle-task runClient \
  --tests mymod:ClientTests --report-dir "$PWD/build/client-run"
```

The isolation adapter is extracted internally into that run's report directory. Automated world
creation needs HeadlessNH in the development runtime. The
[client guide](client-tests.md#automated-development-runs) shows how to include it when
`hqaHeadlessNH=true`.

## Existing packaged launchers

For a packaged command, the supervisor accepts the launcher's prepared argv and environment:

1. Install the reobfuscated HQA and test JARs plus HeadlessNH in an isolated pack.
2. Use its normal bootstrapper to prepare libraries and natives.
3. Write `launch.argv` with one literal argument per line, starting with an absolute Java executable.
4. Use a game directory without `saves`, and a report directory that does not already exist.

```bash
python3 hqa-tools.pyz run-client --cwd /path/to/isolated-pack/.minecraft \
  --argv-file /path/to/isolated-pack/launch.argv \
  --env-file /path/to/isolated-pack/launch.env \
  --tests mymod:ClientTests --report-dir /path/to/new-reports --timeout 900
```

Linux supervision needs `Xvfb` and `xdotool`. `--xvfb` and `--xdotool` can select installed binaries.
The tool owns a private display, focus, launch deadline, cancellation grace, and only its invocation's
process group. It installs HQA's client settings and the HeadlessNH completion gate. The packaged
directory must be isolated and contain no `saves`; reports must be new. See
[Real client scenarios](client-tests.md) for all launch options and runtime requirements.

The command must use the same game directory as `--cwd`; an explicit `--gameDir` must match it.
An optional `launch.env` contains one shell-style environment assignment per line, with quoted values
as needed; they are parsed as literal values without shell evaluation.

The default `--headless singleplayer` settings enable HeadlessNH world creation and hold its
world-loaded gate while HQA executes. Use `--headless existing` to preserve a launcher's navigation
settings while retaining the completion gate. Extra arguments after `--` are forwarded to Gradle,
or inserted as JVM arguments in a packaged launch. They cannot override managed HQA/HeadlessNH settings.

## Integrate with an existing launcher

If CI already owns its launcher and display, use that lifecycle and the archive's `verify` command.
The launcher must prepare a fresh game/report directory, keep native input focused on its window,
bound process execution, and retain logs and reports. HQA starts once an integrated world and player
have rendered, runs the selected cases, completes cleanup, and exits the game with its result code.

Pass these properties to the **Minecraft client JVM**, replacing the selector and report path:

```text
-Dhorizonqa.mode=ci
-Dhorizonqa.client=true
-Dhorizonqa.world=normal
-Dhorizonqa.autoRun=true
-Dhorizonqa.stopServer=true
-Dhorizonqa.turbo=1
-Dhorizonqa.tests=mymod:ClientTests
-Dhorizonqa.reportDir=/absolute/path/to/new-client-reports
-Djava.awt.headless=false
```

For Gradle Minecraft tasks, pass every property through its own `--mcJvmArgs`.
Keep one effective value per property in a prepared Java command.

When using HeadlessNH, enable its navigation mode and set
`-Dheadlessnh.gate.worldloaded=hqa-complete.gate`. Keep that gate absent until HQA finishes so
HeadlessNH does not disconnect during testing. World-loaded markers indicate progress; wait for HQA's
reports and process completion before closing the client. The singleplayer startup budget is 600
seconds. If the same client first connects to a dedicated server, that phase must finish within the
startup budget; client case budgets begin only after the integrated world is ready.

On Linux, position the native window at `(0, 0)` on the test display and give it actual input focus.
HeadlessNH's `forcefocus` setting alone does not establish OS focus. Use separate report directories
for dedicated-server and client runs, verify both, and retain the full directories even on failure.

On deadline, create `stop-client` in the client report directory to request cancellation. Collect a
thread dump from the owned JVM, allow a bounded cleanup grace period, then terminate only processes
owned by the launch. Deadlines, forced termination, crashes, and missing/incomplete reports are
infrastructure failures, even if a partial report claims success. The bundled supervisor handles
these steps when using `run-client`.

## Verify reports

Record the invocation's Unix start time before launching, then verify the completed reports:

```bash
started_at="$(date +%s)"
# Launch and await your reported run here.
python3 hqa-tools.pyz verify /path/to/reports \
  --tests mymod:ClientTests --earliest "$started_at"
```

`--tests` checks the automatic run's selector. Omit it for a manually selected RCON reported batch;
the JVM configuration snapshot does not contain its command arguments. `--earliest` is the invocation's
Unix start time and defaults to `0`. The verifier requires matching, complete JSON and JUnit evidence;
missing, stale, malformed, contradictory, or empty success reports are exit `2`.

Both commands preserve optional failures as exit `0`/JUnit-skipped, required failures/timeouts as
exit `1`, and infrastructure/incomplete/reporting failures as exit `2`. Verification alone cannot prove
that a process exited cleanly: an existing launcher must also reject deadline/forced termination,
crashes, and inconsistent process exits. `run-client` performs those checks automatically.
