---
title: CI tooling
description: One versioned tool for automated client launches and strict report verification.
---

# CI tooling

Horizon-QA remains a Minecraft mod. Install HQA and the consumer's test JARs and use the existing
Minecraft launcher. The optional tools below automate Linux CI execution; the mod does not import
them, and mod authors do not copy helper scripts into their repositories.

## One versioned artifact

`./gradlew hqaTools` produces `build/libs/horizonqa-<version>-tools.pyz`. The archive contains the client
supervisor, report verifier, Gradle isolation adapter, version, and license. It runs with Python 3.9+
and the standard library, without a source checkout, installed Python package, or adjacent helper files.
`assemble` builds it, and `check` runs its protocol and standalone archive tests.

The existing Maven publication includes this archive as
`com.github.GTNewHorizons:Horizon-QA:<version>:tools@pyz`. CI maintainers acquire one reviewed release
centrally and pin its version. Consumer mods opt into the shared workflow with a selector. They do
not download scripts or change their launcher. The local publication configuration is implemented;
publishing a release and adopting the downstream workflow remain separate deployment steps.

Use the same archive for both commands:

```bash
python3 hqa-tools.pyz --version
python3 hqa-tools.pyz run-client --help
python3 hqa-tools.pyz verify --help
```

`launch-result.json` records `toolsVersion` alongside the process outcome. HQA's schema-3 JSON and
JUnit protocol is unchanged.

The supervisor owns a private X11 display. After reading launch environment overrides, it sets
`DISPLAY`, `XDG_SESSION_TYPE=x11`, and `SDL_VIDEO_DRIVER=x11`, and removes `WAYLAND_DISPLAY`.
This keeps native input and the game window on the same display on Wayland hosts. The choices follow
[GLFW's Linux platform selection](https://github.com/glfw/glfw/blob/3.4/src/platform.c#L78-L91)
and [SDL's video-driver selection](https://wiki.libsdl.org/SDL3/SDL_HINT_VIDEO_DRIVER).
Graphics-driver or native-library startup failures still produce infrastructure exit 2 and retain
their logs/crash reports.

## Development shortcut

In the Horizon-QA repository, run the focused client smoke suite with:

```bash
./gradlew hqaClientTest
```

This builds the archive and delegates to its supervisor. Each invocation chooses a new directory
under `build/horizonqa/`, which the printed launch summary identifies. It does not overwrite prior
reports or a developer's saves. The task is intentionally separate from `build` because it boots
Minecraft and requires a display/input environment.

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
creation needs HeadlessNH in the development runtime; HQA's examples already declare it.

## Existing packaged launchers

For a packaged command, the supervisor accepts the launcher's prepared argv and environment:

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

Daily CI already has a launcher and a private display. Keep that lifecycle: its dedicated RCON phase
is followed by singleplayer client tests. The daily launcher waits for HQA completion and uses this
archive's `verify` command. It does not need the standalone supervisor or a second display.

## Verify reports

```bash
python3 hqa-tools.pyz verify /path/to/reports --tests mymod:ClientTests --earliest 1791740000
```

`--tests` checks the automatic run's selector. Omit it for a manually selected RCON reported batch;
the JVM configuration snapshot does not contain its command arguments. `--earliest` is the invocation's
Unix start time and defaults to `0`. The verifier requires matching, complete JSON and JUnit evidence;
missing, stale, malformed, contradictory, or empty success reports are exit `2`.

Both commands preserve optional failures as exit `0`/JUnit-skipped, required failures/timeouts as
exit `1`, and infrastructure/incomplete/reporting failures as exit `2`. Verification alone cannot prove
that a process exited cleanly: an existing launcher must also reject deadline/forced termination,
crashes, and inconsistent process exits. `run-client` performs those checks automatically.

Concrete central workflow acquisition and the daily lifecycle contract are in
[Client CI integration](../contributing/client-ci-integration.md).
