---
title: Client CI integration
description: Concrete local integration instructions for the shared package and daily workflows.
---

# Client CI integration

These instructions target the pinned repositories in [the architecture decision](client-testing-decision.md).
They are proposed downstream changes, not deployed workflows. All framework work and validation remain local.

Copy `run-client-tests.py`, `verify-horizonqa.py`, and `client-test-isolation.init.gradle` together from HQA's
`scripts/` directory. Pin a reviewed HQA commit when downstream adopts them; do not fetch a floating script
during a test. Install `xvfb xdotool` and Python 3.9+. The launcher writes a separate `launch-result.json`;
both its exit code and the game reports must be retained. Do not treat a marker, Gradle success, or only
`status == passed` as proof that requested cases completed.

## GTNH-Actions-Workflows package builds

In `.github/workflows/build-and-test.yml`, add a `workflow_call` string input `hqa-client-tests`, default
empty. An empty value keeps existing package jobs unchanged. Consumers opt in with their namespace,
holder, or exact client case IDs and add HeadlessNH's development dependency to their `runClient` runtime.
After build and server tests, run a new opt-in stage:

```yaml
- name: Horizon-QA client cases
  if: inputs.hqa-client-tests != ''
  env:
    HQA_CLIENT_TESTS: ${{ inputs.hqa-client-tests }}
  run: |
    python3 hqa-tools/run-client-tests.py \
      --cwd "$GITHUB_WORKSPACE" --gradle-task runClient \
      --tests "$HQA_CLIENT_TESTS" \
      --report-dir "$RUNNER_TEMP/hqa-client" --timeout 900
- name: Retain client results
  if: always() && inputs.hqa-client-tests != ''
  uses: actions/upload-artifact@v4
  with:
    name: ${{ github.repository_id }}-hqa-client
    path: ${{ runner.temp }}/hqa-client
    if-no-files-found: error
```

The pinned shared workflow has one build job and installs toolchains from its `list-jdk-versions` step.
Use `runClient25` or another supported RFG task explicitly when enabling a modern development runtime. HQA emits Java 8
bytecode. For package environments without a modern `runClient` backend, test Java 8 in development and
use an assembled modern pack for lwjgl3ify coverage. The launcher creates/focuses a private display and
redirects `runClient` to a fresh game directory. It forwards each HQA JVM property separately through
`--mcJvmArgs`. Keep this stage out of the publication condition when it fails.

Replace the current server verification that accepts only an integer `exitCode` with:

```bash
python3 hqa-tools/verify-horizonqa.py "$HQA_SERVER_REPORT_DIR" --tests "$HQA_SERVER_TESTS"
```

The automatic server stage must set `horizonqa.tests` to that selector and use a freshly created report
directory. The verifier returns `0` for optional failures, `1` for required assertions/timeouts, and `2`
for invalid/incomplete evidence or infrastructure failure. Existing report/JUnit consumers keep schema 3.

## DreamAssemblerXXL daily dual tests

In `.github/workflows/modpack-test.yml`, add an opt-in `hqa-client-tests` string input and pass it to
`scripts/tests/dual_tests.sh`. Keep the existing server flags, Java 8/26 matrix, bootstrap argv, RCON
reported tests, serverloaded gate, recordings, and stage summaries. Client and server need distinct
directories: for example `$RUN_DIR/server-hqa` and `$RUN_DIR/client-hqa`. Configure both explicitly;
the historical workflow put server reports directly in `$RUN_DIR`.

In `scripts/tests/headless_client.sh`, add these JVM arguments to the **client**, preserving its normal
Java, natives, lwjgl3ify, and `headlessnh.combined=true` launch arguments:

```text
-Dhorizonqa.mode=ci
-Dhorizonqa.client=true
-Dhorizonqa.world=normal
-Dhorizonqa.autoRun=true
-Dhorizonqa.stopServer=true
-Dhorizonqa.turbo=1
-Dhorizonqa.tests=<hqa-client-tests>
-Dhorizonqa.reportDir=<absolute RUN_DIR/client-hqa>
-Djava.awt.headless=false
-Dheadlessnh.gate.worldloaded=hqa-complete.gate
```

Create `client-hqa` exclusively before launch; reject reuse. Remove obsolete copies of the same JVM
properties from prepared argv so last-property-wins cannot override this contract. Use the existing
private Xvfb and update `focus_client_window` to position the window at `(0, 0)` before focusing it.
In its `if wid=$(get_client_window)` branch use:

```bash
xdotool windowmove "$wid" 0 0 && xdotool windowfocus "$wid" && return 0
```

Search visible windows by class on that private display if the pack changes Minecraft's title.
lwjgl3ify 3.0.37 initially reports window coordinates `(0, 0)` until a native move event;
explicit positioning gives AWT and the public LWJGL facade the same coordinates.
`headlessnh.forcefocus` alone is insufficient.
The dedicated connection phase does not consume client test ticks. HQA starts only after HeadlessNH
disconnects and a singleplayer world/player has rendered with no loading GUI.

Replace the worldloaded watcher branch that sleeps five seconds and closes the window. With client
tests enabled, await the client process and HQA reports within `CLIENT_RUN_TIMEOUT`; do not open the
worldloaded gate or close the window at that marker. HQA writes reports, restores state, and exits the
client itself. On deadline, touch `$RUN_DIR/client-hqa/stop-client`, collect an owned-JVM thread dump,
allow at most 15 seconds, then terminate only the invocation's process group. Record forced termination
and crash logs as infrastructure exit `2`, even if an older/partial report claims success.

In `scripts/tests/verify_client.sh`, after preserving the existing mainmenu/serverloaded/worldloaded
stage checks, require both:

```bash
python3 hqa-tools/verify-horizonqa.py "$RUN_DIR/client-hqa" \
  --tests "$HQA_CLIENT_TESTS" --earliest "$CLIENT_STARTED_EPOCH"
```

and a non-forced owned process exit consistent with the report. Exit `0` plus a valid report containing
optional failures is success; process/JUnit infrastructure errors are exit `2`. Retain the entire client
report directory and crash reports even on failure. The standalone HQA launcher demonstrates the same
contract against packaged `launch.argv` without a source checkout or Gradle wrapper.

`scripts/tests/verify_server.sh` should run the same verifier on `server-hqa` without `--tests` when
RCON selected the run and `horizonqa.tests` was unset. Preserve the daily's nonempty-selection condition
and RCON completion wait; JSON and XML must agree. A local focused `--tests` check for a manual RCON run
would need an explicit expected-ID manifest because its property snapshot does not contain command
arguments. No client-only classes are loaded by the dedicated server.

The assembled pack must contain new HQA and consumer test JARs; ordinary consumer holders continue to
run on the server. Real production client holders are downstream adoption work. `ClientInteractionTests`
is a framework smoke selector for local pack proof, not a production GUI toolkit dependency.
