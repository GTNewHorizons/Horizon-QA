---
title: Client testing validation
description: Local runtime proof, commands, artifact locations, and external limitations.
---

# Client testing validation

The initial runtime evidence below was collected before tooling was bundled, at implementation
commit `074f7525d49aca6360a43bf1d52e831cd3615110`. The implementation was subsequently moved into
`/home/eliott/.t3/worktrees/Horizon-QA/t3-dc388de4` on `feat/client-testing-support`. Commands below
show the current tools interface; the original exact invocations remain in the retained logs.
The additional [tools distribution checks](#tools-distribution-and-development-shortcut) exercise
the archive and shortcut from that active worktree.

The initial runtime run was executed on Linux on 2026-10-11 from local branch `feat/client-runtime-tests`, worktree
`/tmp/horizonqa-client-tests`, based on upstream master `e05abc05be88e9072c7ed298e4ae81c158213515`.
Upstream and PR #259 were rechecked read-only: master/base and PR head remained the commits in
[the decision](client-testing-decision.md); PR metadata remained unchanged. The original checkout
and installed client/server packs were preserved. No push, publication, PR mutation, or remote run occurred.

## Build and contract checks

```bash
./gradlew build
./gradlew javadoc
mkdocs build --strict
./gradlew hqaToolsTest
git diff --check
```

Build, Javadoc, strict documentation, and diff checks passed. Gradle ran 279 unit tests with no
failures/errors/skips. The seven Python tests passed, including missing/stale/malformed/incomplete
reports, empty false success, JSON/JUnit disagreement, qualified selectors, and malformed metadata.
Logs are in `/tmp/hqa-client-evidence/{final-build,final-javadoc,build-javadoc,mkdocs,python-tests}.log`.
Javadoc emitted warnings; there were no documentation errors.

## Real development client

Java `1.8.0_492` (Zulu) and LWJGL `2.9.4-nightly-20150209`, confirmed from the actual owned JVM.
HeadlessNH `1.1.1`, a fresh game directory, and a private Xvfb display were used for each invocation.
The launcher forwards every HQA JVM flag through a separate `--mcJvmArgs`.

```bash
python3 hqa-tools.pyz run-client --gradle-task :examples:runClient \
  --tests horizonqaexamples:ClientInteractionTests \
  --report-dir /tmp/hqa-client-evidence/dev-success-notify-final --timeout 300
```

This passed all three cases: native Shift-click on a one-pixel target after a real GUI scale change,
native Escape, real chest opening/take/return with authoritative inventory/cursor assertions, and
successive tests with restored client state and batch hooks. The display was 959×541.

Intentional cases were selected individually, followed by `ClientFailureTests.zCleanupAfterFailure`.
Use the same command with `--tests horizonqaexamples:ClientFailureTests.<method>,horizonqaexamples:ClientFailureTests.zCleanupAfterFailure`
and a new report directory:

| Method | Verified result | Artifact directory under `/tmp/hqa-client-evidence/` |
|---|---|---|
| `aRequiredFailure` | Required failure + cleanup case passed; exit 1 | `dev-required-final` |
| `bOptionalFailure` | Optional JUnit-skipped + cleanup case passed; exit 0 | `dev-optional-ready-final` |
| `cTimeoutWithInputHeld` | Timed out + cleanup case passed; exit 1 | `dev-timeout-final` |
| `dCancellation` | Execution aborted; subsequent case not started; exit 2 | `dev-cancel-final` |
| `eRenderCrashIsInfrastructure` | Real renderer crash remains infrastructure despite optional annotation; exit 2 | `dev-render-crash-final` |
| `fWorldUnloadIsInfrastructure` | World loss/server shutdown; exit 2 | `dev-world-unload-final` |
| `gCancellationWhileWorldUnloads` | Terminal shutdown interrupts orderly cleanup; exit 2 | `dev-cancel-unload-final` |
| `hServerTickCrashIsInfrastructure` | Real world tile tick crashes integrated server; optional case remains infrastructure; exit 2 | `dev-server-crash-reviewed-final` |

Cleanup checks released keys/buttons, closed the screen, restored settings, and prevented queued stale
mutations. Assertion failure and timeout allowed the following case only after teardown. Cancellation,
world loss, and crashes retained incomplete cases and infrastructure details. Each directory contains
JSON, JUnit, launch summary/logs, and screenshots when rendering remained available. Exact invocations
are recorded in sibling `*.validation.json` files. The reviewed server-crash rerun includes its Forge
crash report and upfront client artifact location even when the server can no longer await cleanup.

## Dedicated-server regressions

`:examples:runServer25` exercised `SequencePhaseTests.phaseOrder` (1 passed, exit 0), `BasicTests`
(2 passed, 2 intentional optional failures/timeouts, 1 assumption skip, exit 0), and
`ReportedRunLifecycleTests` (intentional before-hook infrastructure error, cleanup case passed, exit 2).
Client-only holders were present and never linked during dedicated discovery. All HQA arguments used
individual `--mcJvmArgs`; exact commands/profiles and reports are recorded in
`/tmp/hqa-client-evidence/dedicated-summary.txt` and `dedicated-{sequence,basic,lifecycle}/reports/`.

## Packaged daily lifecycle and supervision

The representative assembled GTNH daily (`2026-10-05+774`) client/server copies use Temurin `26.0.2.1+1`, lwjgl3ify
`3.0.37`, LWJGL `3.4.3`, HeadlessNH `1.1.1`, and Prism bootstrap `1.0.3`; no Gradle or source checkout
is needed by the game. Runtime manifests record the 255 client mod entries, libraries, and jar hashes.

The local combined run connected to an owned loopback dedicated server, held `serverloaded.gate`, ran
an exact reported HQA case over RCON, verified server JSON/JUnit (1 passed, exit 0), then opened that
gate and entered singleplayer. All three client cases passed, the worldloaded gate remained closed,
and the client wrote its own reports and exited 0 without forced termination or crash reports.
Commands, gate history, manifests, reports, screenshots, and both logs are in
`/tmp/hqa-client-evidence/daily-dual/summary.md`. This run preceded the final scale-change smoke.
The final standalone packaged run passed all three cases with the production HQA/examples JARs,
without forced termination, timeout, or crash reports, in
`/tmp/hqa-client-evidence/packaged-final/client-hqa-final-success/`. Exact commands, runtime inputs,
hashes, and earlier failed probes are recorded in `/tmp/hqa-client-evidence/packaged-final/summary.md`.
The same final packaged runtime then ran `bOptionalFailure`, `cTimeoutWithInputHeld`, and
`zCleanupAfterFailure`: optional JUnit-skipped, required timeout, following cleanup case passed;
overall exit 1 with no infrastructure issues or forced termination. Reports and screenshots are in
`/tmp/hqa-client-evidence/packaged-final/failures-hqa-final/`.

Fullpack probing exposed two required corrections: explicitly position the native window and correct
the pointer from public input coordinates, and notify clients when clearing fixture terrain. The native
click and Escape examples each allow a maximum 200 END ticks for cold rendering; they still complete
on observed frame/input state. Java 8 passed the same interactions with the final runtime and the earlier,
shorter Escape ceiling. The schema and outcome policy remain unchanged.

The launcher was also tested with an owned stalled JVM: a five-second deadline plus one-second
cancellation grace produced exit 2 in approximately six seconds, retained a real SIGQUIT thread dump,
and terminated only that process group. A JVM exiting 0 without reports was rejected as exit 2.
Artifacts/commands are in `/tmp/hqa-client-evidence/supervisor-probe/`.

## External limitations

Spark 1.10's background native async-profiler crashed on Java 26 in the unmodified copied pack
(`Recording::writeCpool`, SIGSEGV). Those launches were correctly rejected as infrastructure failures.
For isolated HQA compatibility proof, `config/config.json` was changed to `backgroundProfiler=false`
only in the disposable copies; mods and runtime backend were retained. Successful modern validation
therefore applies with that mitigation. Unmodified Java 26 with background profiling remains failed,
with exact argv/environment and `hs_err_pid641399.log` / `hs_err_pid657229.log` retained in the packaged
and daily-dual artifacts. Restore `backgroundProfiler=true` in a fresh copy and rerun its recorded argv
to reproduce; use a new report directory.

The exact packaged supervisor command is recorded in `packaged-final/summary.md`; equivalently, with
the recorded private-display binaries and environment:

```bash
python3 hqa-tools.pyz run-client --headless singleplayer \
  --argv-file /tmp/hqa-client-evidence/packaged-final/launch.argv \
  --cwd /tmp/hqa-client-evidence/packaged-final/client/.minecraft \
  --env-file /tmp/hqa-client-evidence/packaged-final/launch.env \
  --tests horizonqaexamples:ClientInteractionTests \
  --report-dir /tmp/hqa-client-evidence/packaged-final/new-reproduction --timeout 600 \
  --xvfb /tmp/hqa-client-evidence/xvfb/root/usr/bin/Xvfb \
  --xdotool /tmp/hqa-client-evidence/xvfb/root/usr/bin/xdotool
```

Archive only that disposable copy's generated `saves` before replaying; the launcher rejects existing
saves and report directories. With the profiler enabled this variant remains failed, not verified.

The opt-in package and daily workflow changes in [Downstream integration](client-ci-integration.md)
were validated locally, and remain undeployed. Production client holders/toolkit adapters require
consumer adoption. Connected dedicated-server GUI execution, pause-menu behavior with HeadlessNH 1.1.1,
and hardware/shader pixel assertions remain outside the selected implementation.

## Fluent authoring follow-up

The 2026-10-13 follow-up starts from local commit
`c34674292638822b51880bba59a271f0e3f09edc` on `feat/client-testing-support` in
`/home/eliott/.t3/worktrees/Horizon-QA/t3-dc388de4`. It adds the fluent interface and named live targets,
scopes native Shift-click through observed release, preserves author source locations without client
linkage in common code, and converts the existing interaction/failure holders. Test IDs and schema 3
remain stable. The button example checks original failure propagation and released Shift before
recovering within the same session; the chest example checks real right-click splitting and left-click
return packets, including the authoritative cursor. Fixture aiming runs once, outside assertion polling.

Fresh evidence is under `build/horizonqa/fluent-evidence/` in this worktree. Earlier `/tmp/` evidence
paths above are historical and are no longer available in this session. The detailed local
`validation-summary.md` contains exact invocations, runtime/backend versions, and targeted display probes.
Client launches use the shipped tools archive, fresh game/report directories, owned supervision,
and separate `--mcJvmArgs` for every HQA property.

| Check | Result | Evidence under `build/horizonqa/fluent-evidence/` |
|---|---|---|
| `./gradlew build javadoc` | 278 Java tests and 8 tools tests; compilation, formatting, Checkstyle and Javadoc passed | `build-javadoc.log`, `build-summary.json` |
| `mkdocs build --strict` | Passed | `mkdocs.log` |
| Java 8 interaction holder with final shipped archive | Three cases passed; Shift-click failure/recovery, exactly one handler click, right-click chest 1/cursor 2, left-click chest 3/cursor empty, following restoration checks | `client-interactions-complete/` |
| Required + optional + timeout + cleanup | Expected exit 1; two JUnit failures, one optional skip, following cleanup passed; no incomplete cases or infrastructure issues | `client-required-optional-timeout-cleanup/` |
| Optional + cleanup | Expected exit 0; optional JUnit-skipped, following cleanup passed | `client-optional-cleanup/` |
| Cancellation + following case | Expected exit 2; following case remained `notStarted`, incomplete execution retained | `client-cancellation-next-final/` |
| Dedicated `SequencePhaseTests.phaseOrder` on Java 25 | Passed; client holders remained discovery-safe | Reports in parent `build/horizonqa/` |
| Archive display-isolation regression | Failed against the original archive's Wayland environment; passed after managed X11 selection | `display-regression-red.log`, `build-javadoc.log` |

The first cancellation attempt overlapped a build and failed before Minecraft during compilation.
It was correctly rejected as infrastructure exit 2; the fresh sequential retry above passed its
expected cancellation contract. The earlier failed attempt remains in `client-cancellation-next/`.
Builds and game launches must run sequentially in a shared worktree.

Java 8 uses Azul `1.8.0_492` and LWJGL `2.9.4`. The attempted modern development task uses JetBrains
`25.0.3`, lwjgl3ify `3.0.20`, and LWJGL `3.4.2-snapshot+2`. Its initial inherited Wayland environment
produced no window on Xvfb and native input/focus failures. The supervisor now fixes `DISPLAY`,
`XDG_SESSION_TYPE=x11`, and `SDL_VIDEO_DRIVER=x11` and removes `WAYLAND_DISPLAY` after environment
overrides; the existing archive test checks that contract without adding a duplicate test. The live
probe confirmed those fields in the owned game JVM. That runtime then consistently crashed in native
`pthread_mutex_lock` during `SDL_CreateWindowWithProperties`, before any game window or HQA report.
Selecting Mesa EGL instead of NVIDIA also reproduced the native crash. These launches remain
infrastructure exit 2, with crash files retained; Java 25 client interaction is **unverified** here.

Reproduce the native blocker with the current archive, extracted display dependencies from the
detailed summary (or installed Xvfb/xdotool), and a new report directory:

```bash
python3 hqa-tools.pyz run-client --gradle-task :examples:runClient25 \
  --tests horizonqaexamples:ClientInteractionTests.aNativeButton \
  --report-dir "$PWD/build/horizonqa/new-java25-reproduction" --timeout 300
```

The final shipped-archive replay under the normal inherited host environment reproduced this same
startup blocker in `client-java25-final-blocker/`; its monitor confirms the delivered archive applies
all managed X11 settings to the actual game JVM. `client-java25-no-wayland-probe/`,
`client-java25-sdl-x11-probe/`, and `client-java25-mesa-probe/`
contain the owned-process environment/window inventories and native crash evidence. Neither the
fluent interface nor the input implementation is invoked before this native startup crash.

A fresh packaged/fullpack replay of this follow-up is also **unverified**. The inspected Prism
instances contain saves and have no prepared launcher argv/environment; installed packs were
preserved. Prepare an isolated copy without saves and its normal bootstrapper's literal argv, then run:

```bash
python3 hqa-tools.pyz run-client --cwd /path/to/isolated-pack/.minecraft \
  --argv-file /path/to/isolated-pack/launch.argv \
  --env-file /path/to/isolated-pack/launch.env \
  --tests horizonqaexamples:ClientInteractionTests \
  --report-dir /path/to/new-results --timeout 900
```

The historical Java 26/fullpack checks above establish the previous implementation, not a fresh
validation of this fluent follow-up. Package/daily workflow integration remains local and undeployed.

## Tools distribution and development shortcut

The follow-up retains the same Java runtime, input behavior, schema-3 reports, and exit policy. It
packages supervision, verification, and the isolation adapter into one `.pyz` artifact and adds the
thin `hqaClientTest` task. Consumer mods do not copy source files. Publication coordinates are stable
across checkout/worktree names because the root project is explicitly named `Horizon-QA`.

Local commands and evidence under `/tmp/hqa-tooling-evidence/`:

| Check | Result | Evidence |
|---|---|---|
| `./gradlew build javadoc generatePomFileForMavenPublication` | Passed; Java checks and 10 tools tests | `build-javadoc.log` |
| `mkdocs build --strict` | Passed | `mkdocs.log` |
| `./gradlew hqaClientTest -PhqaReportDir=/tmp/hqa-tooling-evidence/dev-success -PhqaTimeout=300` | Real Java 8 client: all three interaction cases passed; clean exit 0 | `dev-success/`, `dev-success-command.json`, `dev-success-gradle.log` |
| `hqaClientTest` with optional failure, required timeout, and following cleanup; no report path supplied | Fresh default directory; JUnit-skipped optional failure, required timeout/exit 1, cleanup passed | `dev-failure-command.json`, `dev-failure-validation.json`, `dev-failure-gradle.log` |
| Single `.pyz` outside a source checkout against the representative Java 26 fullpack | All three interaction cases passed; clean exit 0 | `packaged-command.json`, `packaged/success/`, `packaged-console.log` |
| Owned stalled JVM, five-second deadline and one-second grace | Exit 2; thread dump retained; owned group terminated | `probe-hang/`, `supervision-and-compatibility.json` |
| Owned JVM exits 0 without game reports | Rejected with exit 2 | `probe-empty/`, `supervision-and-compatibility.json` |
| New verifier against prior dedicated sequence/basic/lifecycle reports | Expected exits 0/0/2; these are compatibility checks, not fresh server boots | `supervision-and-compatibility.json` |
| Central package workflow patch | Applies to the pinned shared-workflow commit; YAML and opt-in selector parsed | `shared-build-and-test.yml` |
| `./gradlew hqaTools --rerun-tasks` | Identical archive SHA256 for unchanged inputs | `archive-reproducibility.txt` |
| Existing `RELEASE_VERSION` override | Archive tests passed; actual Maven classifier and embedded version agree | `release-version-check.log`, `publication-check.log` |

The modern packaged copy preserves the previously documented Spark mitigation. The tools archive is
the only file in `standalone/`; its launch uses the pack's prepared argv, libraries, and natives without
Gradle or repository sources. Ten tools tests also exercise the shipped command outside the checkout,
verify optional/required/invalid-report exits, and confirm internal Gradle resource extraction and
separate `--mcJvmArgs` forwarding. Fake launcher probes establish only that resource/command contract;
the Java 8 and Java 26 interaction runs establish real Minecraft behavior.

The Maven classifier is configured locally and its coordinates are checked through generated metadata.
No artifact was published. Network acquisition of a released archive and deployment of the central
package/daily changes remain unverified until the reviewed artifact is released and workflows adopt it.

## Test consolidation review

The follow-up review covers the client/testing additions on `feat/client-testing-support`, starting
from `32d3faa2c3d3db9e8e6279c93f8f346b350eed04`. Existing unrelated suites remain unchanged.

Report exit policies are now tested once through the shipped archive, outside the source checkout.
An explicit outcome table covers successful, optional, required, timeout, infrastructure, running,
and not-started cases. Invalid reports must produce rejection diagnostics; valid infrastructure
reports must be accepted as exit 2. Shared archive setup replaces imports between test modules.
Missing archive configuration fails clearly rather than making the entire tools suite skip.
Eight tools tests replace ten; client flag coverage joins the existing property matrix, leaving
278 Java tests. The nine async lifecycle tests retain distinct submission, retry, phase, cancellation,
cleanup, fatal-error, and shutdown coverage, with shared cleanup setup and a five-second deadline.

The final real client case no longer repeats the native-button scenario. It checks cleanup from the
preceding cases: authoritative inventory, cursor, selected slot and game mode on the server thread;
screen, released input and GUI scale on the client thread. Batch cleanup releases its saved state.
Assertion, timeout and server-crash examples share held-input fixture preparation while keeping
their distinct failure paths.

Local evidence is retained in `/tmp/hqa-test-review-evidence/`. Build, Javadoc, and the tools suite pass.
The real Java 8 interaction suite passes all three cases. A separate client run produces required and
optional assertion failures, a required timeout, and a passing subsequent cleanup case: expected
exit 1, two JUnit failures, one JUnit skip, no infrastructure error or forced termination.
Six disposable verifier mutations are caught by assertions: optional-failure policy, stale reports,
incomplete execution, qualified selector identity, JSON/JUnit disagreement, and missing tick metadata.
The mutation archive copies do not modify repository or release sources.

Earlier `/tmp/hqa-client-evidence/` and `/tmp/hqa-tooling-evidence/` runtime inputs are no longer present.
Display dependencies were downloaded and extracted locally for this follow-up; system packages and
installed packs were not changed. No fresh packaged Java 26 replay is claimed for this test-only change:
the earlier disposable pack, `launch.argv`, and `launch.env` are unavailable. Recreate an isolated pack
and use the [packaged launch command](../guide/client-tests.md#packaged-and-ci-launch) to repeat it.
