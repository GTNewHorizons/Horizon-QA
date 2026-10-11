---
title: Client testing validation
description: Local runtime proof, commands, artifact locations, and external limitations.
---

# Client testing validation

Executed on Linux on 2026-10-11 from local branch `feat/client-runtime-tests`, worktree
`/tmp/horizonqa-client-tests`, based on upstream master `e05abc05be88e9072c7ed298e4ae81c158213515`.
Upstream and PR #259 were rechecked read-only: master/base and PR head remained the commits in
[the decision](client-testing-decision.md); PR metadata remained unchanged. The original checkout
and installed client/server packs were preserved. No push, publication, PR mutation, or remote run occurred.

## Build and contract checks

```bash
./gradlew build
./gradlew javadoc
mkdocs build --strict
python3 -m unittest discover -s scripts -p 'test_*.py' -v
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
python3 scripts/run-client-tests.py --gradle-task :examples:runClient \
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
python3 scripts/run-client-tests.py --headless singleplayer \
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
