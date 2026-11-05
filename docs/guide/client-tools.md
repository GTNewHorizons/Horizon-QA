---
title: Portable test tools
description: Supervise isolated client and dedicated-server tests with shared cleanup and retention.
---

# Portable test tools

The optional `tools` ZIP contains the shared client/server supervisor and report verifier used in this repository.
Download a pinned Maven artifact `com.github.GTNewHorizons:Horizon-QA:<version>:tools@zip` and unpack it.
No Horizon-QA source checkout is needed.
The consumer still supplies its Gradle wrapper, RFG runtime task, Horizon-QA dependency and test classes.

## Launch from a consumer project

Supervision needs JDK 17+ and Python 3.9+ on Windows or Linux.
Minecraft uses the Java runtime selected by the consumer's RFG task.
Client launches on Linux also need the graphics environment described in [Client testing](client-tests.md). Dedicated servers do not.

Run the consumer's wrapper from its project directory:

```sh
./gradlew -p /path/to/unpacked/scripts/client-tests runClientTests \
    --project-root "$PWD" --client-task :runClient --tests mymod:ScreenTests
```

On Windows use `gradlew.bat` and pass the consumer's absolute directory to `--project-root`.
The task retains isolated game directories, classpath snapshots, preparation locking and owned-process cleanup.
The same [launch options and result policy](#one-unattended-command) apply to source and archive use.

Python defaults to `python` on Windows and `python3` elsewhere.
Use `--python /absolute/path/to/python` or `-PhorizonQaPython=/absolute/path/to/python` to select an interpreter.
The same `horizonQaPython` property selects the interpreter for this repository's `hqaToolsTest` task.
An unavailable interpreter produces an infrastructure failure. No interpreter fallback is attempted.


## One unattended command

From an HQA source checkout (`gradlew.bat` on Windows):

```sh
./gradlew -p scripts/client-tests runClientTests \
    --tests horizonqaexamples:ClientSmokeTests.clickEscapeAndReopen
```

The task creates a fresh report directory, starts the target project's `:examples:runClient` at 1280 by 720, selects the named tests, and enforces a 600-second wall-clock deadline. It writes `launch-result.json` with the existing exit codes: `0` for success, `1` for required test failure, and `2` for infrastructure failure or an incomplete run. Gradle itself returns a generic build failure for either nonzero result, so automation must inspect the JSON code. An intentional failure example is `horizonqaexamples:ClientSmokeTests.intentionalFailure`.

For a consumer checkout with Horizon QA already configured as a development dependency:

```sh
./gradlew -p scripts/client-tests runClientTests \
    --project-root /path/to/consumer \
    --client-task :runClient \
    --tests mymod:ScreenTests.openAndReturn
```

The standalone supervisor uses the consumer's Gradle wrapper and existing RFG task. It does not add dependencies or change consumer packaging. Keep client test classes out of normal published artifacts. During local framework development, `--horizon-qa-jar /path/to/horizonqa-dev.jar` forwards `-PhorizonQaJar` to a consumer that explicitly supports that override.

Use repeatable `--gradle-argument=-PusesMixinDebug=false` options for additional target build arguments. Each value remains one argument, without shell interpolation. Do not override client mode, selection, game directory, launch identity or report directory. The target build uses a private, single-use Gradle daemon so the supervisor never terminates a shared daemon.

`--report-dir` must name a new directory. `--timeout-seconds` bounds the whole launch including Gradle preparation. `--shutdown-seconds` defaults to 20. On expiry the supervisor requests orderly shutdown with a marker and attempts a bounded thread dump of the identified client. After the grace period, it terminates only the wrapper and descendants observed as belonging to that invocation. This also covers a build that hangs before Minecraft starts. Missing or malformed reports, incomplete execution and forced termination produce launch code `2`.

The same inputs are available as Gradle properties: `horizonQaTests`, `horizonQaProjectRoot`, `horizonQaClientTask`, `horizonQaClientReportDir`, `horizonQaTimeoutSeconds`, `horizonQaShutdownSeconds`, `horizonQaJar` and `horizonQaPython`. The supervisor and the isolation init script support configuration cache. `scripts/tests/portable-client-launcher/verify.py` exercises real owned processes without Minecraft, including startup hangs and an unrelated process that must remain alive.

The `Client tests` CI workflow boots real clients under Xvfb. It checks a passing GUI scenario, a late render failure, fatal cleanup propagation, cancellation of an active run and two overlapping clients. It also rejects managed-argument overrides before target startup. It validates both report files, expected exit semantics and orderly process termination, and retains reports, logs, crash reports and screenshots on failure.

Before starting tests, CI prepares the pinned Gradle distribution with `./gradlew --version`. Transient HTTP server errors and connection timeouts allow up to three attempts with 5- and 10-second waits. Other errors stop immediately. The bootstrap log is retained, and this step never repeats a client scenario.


The pinned Gradle runtime retries transient dependency repository failures up to five attempts, waiting 5, 10, 20 and 40 seconds between attempts. The repository's `gradle.properties` configures this for local builds and CI. Missing artifacts and authentication failures are not retried. Client scenarios are never rerun by this policy, and existing launcher and CI deadlines still apply.

## Isolated dedicated-server runs

Use the same distribution and supervisor for server tests:

```sh
./gradlew -p /path/to/unpacked/scripts/client-tests runServerTests \
    --project-root "$PWD" --server-task :runServer \
    --tests mymod:MachineTests --accept-eula
```

`--accept-eula` explicitly accepts the [Minecraft EULA](https://www.minecraft.net/eula) for the fresh test server. Without it, the launcher refuses to start a server. The owned game directory receives `eula.txt` and `server.properties` with `online-mode=false`, avoiding RFG's interactive setup prompts. Horizon-QA automatic CI server behavior skips network binding.

Server launches use `horizonqa.client=false`, CI mode, a void world, automatic execution and shutdown. Their default target is `:examples:runServer`, configurable with `--server-task` or `horizonQaServerTask`. `horizonQaServerReportDir` configures their report directory. They share selectors, deadlines, process ownership, verification, storage options and retention with `runClientTests`. Window arguments and audio options apply only to clients. A server that exceeds its external deadline is terminated after the shutdown grace period, without the client's orderly-cancellation marker.

Both supervised tasks default to fresh directories under the consumer's `build/horizonqa/`. Direct RFG `runClient` and `runServer` tasks do not opt into this storage policy. Their existing working directories and worlds remain under the caller's control.

## Storage and retention

The portable launcher removes `game/` and `classpath/` after its owned processes stop, for successful and failed launches alike. JSON/XML reports, screenshots, launch logs and thread dumps remain. Game logs and crash reports are copied to `diagnostics/logs/` and `diagnostics/crash-reports/` before removal. Use `--keep-runtime-files` to retain the full game and classpath for debugging, or `--no-keep-runtime-files` to override a repository default.

By default, only the five most recently completed runs are retained for the same consumer project under the same report parent directory. Older runs are removed in full, including their reports and any retained game. Successful and failed runs share this limit. Set a positive `--retained-runs N` to change it.

A consumer can put defaults in its root `gradle.properties`:

```properties
horizonQaRetainedRuns=5
horizonQaKeepRuntimeFiles=false
```

Launcher options and launcher Gradle properties (`-PhorizonQaRetainedRuns=N`, `-PhorizonQaKeepRuntimeFiles=true`) override consumer defaults. Pass launcher properties directly, rather than through `--gradle-argument`. Group report directories under one parent to share its retention limit.

Retention runs at launch completion and only considers directories with matching launcher ownership metadata and completed launch results. Active runs, other projects and unmarked directories are excluded. Collection is serialized per report parent and never follows symbolic links during removal. Existing unmarked runs are not adopted or deleted. A process killed before completion leaves an unmarked directory for manual inspection. Cleanup errors fail the launch instead of reporting successful storage maintenance.

## Parallel client launches

Each launcher invocation uses `<ReportDir>/game` as its writable Minecraft directory. The companion Gradle init script sets the selected RFG task's `workingDir` before RFG calculates `--gameDir`. Configuration, options, logs and scratch saves therefore belong to one launch. `launch-result.json` records both `gameDir` and `launchId` alongside the existing result fields. The game directory starts fresh and does not copy settings or manually installed mods from the usual development run directory. Dependencies supplied by the build remain on its runtime classpath.

After the task's build dependencies finish, the init script snapshots classpath entries under the root project's and subprojects' configured build directories into `<ReportDir>/classpath`. This includes development JARs and classes/resource directories. The client uses those copies in the original classpath order, so subsequent rebuilds do not replace those artifacts underneath a running client. External dependency-cache entries remain shared. Outputs from independent included builds or other external file dependencies are outside this snapshot.

Run separate launcher invocations in parallel with distinct report directories, or omit `--report-dir` to generate one per invocation. Each process runs its selected batch serially through the existing runner. Distribute selectors between launches when splitting a suite. Launchers for the same project automatically serialize build preparation with a file lock, then release it when the classpath snapshot and game directory are ready. The next build can prepare while the previous game keeps running. Gradle retains its normal caches. Waiting for preparation counts toward the launch deadline.

This coordination covers launches through the portable launcher. Keep unsnapshotted external dependencies stable throughout the run, and do not delete an active report directory, including through `clean` when reports live under `build`. Unrelated build commands do not acquire the launcher's preparation lock.

Run `python scripts/tests/parallel-clients.py --report-dir build/client-parallel-check` to verify two real games together. Both instances of `ClientParallelTests.clickWhileAnotherClientIsRunning` open a screen and write their own readiness marker. The script waits for both live clients before releasing their independent GUI clicks, then verifies both results and distinct launch identities. The example requires its exact selector and is skipped during broader example runs.

## Verify a completed run

The supervisor always runs the shared verifier after the target process exits.
Its output is saved as `verification.log` alongside the game and launch reports.
Missing, stale, malformed, incomplete or contradictory evidence produces infrastructure exit `2`.

The command can also verify reports produced by another launcher:

```sh
python /path/to/unpacked/tools/verify-client-reports.py /path/to/reports \
    --tests mymod:ScreenTests --earliest "$launch_started_at"
```

Set `launch_started_at` to the Unix start timestamp recorded before that launch.
Omit `--tests` only when verifying a run selected independently, such as a manual command.
The verifier accepts schema `5` only and checks:

- Matching JSON/JUnit identities, outcomes and counts
- The selected namespace, holder, ID prefix or parameter row
- Legitimate missing-mod family skips and explicitly permitted empty all-test runs
- Required versus optional failures and infrastructure outcomes
- Report paths belonging to the invocation and modification times after its start

Verification returns the report's exit code: `0` for success including optional failures, `1` for required failures,
and `2` for infrastructure or invalid evidence.
The supervisor additionally checks the owning process's completion and exit status.
The target Gradle process must exit `0` for a successful report and `1` for either nonzero HQA outcome.
Report verification alone does not prove a clean process exit.

## Build the archive

```sh
./gradlew hqaTools
```

The reproducible archive is written under `build/libs/` and is published with classifier `tools` and extension `zip`.
It contains the supervisor Gradle build, isolation script, Python verifier, usage notes and license.
It does not package Minecraft, mods, dependency caches or a second process supervisor.
