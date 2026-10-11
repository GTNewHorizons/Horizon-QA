---
title: Client testing decision
description: Evidence, runtime boundaries, and reuse decisions for real client scenarios.
---

# Client testing decision

Inspected on 2026-10-11:

* Horizon-QA upstream master and PR base: [`e05abc05be88e9072c7ed298e4ae81c158213515`](https://github.com/GTNewHorizons/Horizon-QA/commit/e05abc05be88e9072c7ed298e4ae81c158213515).
* [PR #259](https://github.com/GTNewHorizons/Horizon-QA/pull/259), complete 80-file diff, four commits through [`6cff237c3b829edce087d771bd1b04b38c3df4e3`](https://github.com/GTNewHorizons/Horizon-QA/commit/6cff237c3b829edce087d771bd1b04b38c3df4e3), discussion (none), all check conclusions, and both build/client check log archives. All checks succeeded; these prove the PR's selected development scenarios, not fullpack portability.
* DreamAssemblerXXL [daily run 38130877487](https://github.com/GTNewHorizons/DreamAssemblerXXL/actions/runs/38130877487), including both runtime jobs and logs, at [`28f22982e2666a00f5a335ec518d706d6b853259`](https://github.com/GTNewHorizons/DreamAssemblerXXL/tree/28f22982e2666a00f5a335ec518d706d6b853259). Current master was separately checked at `c1d78cdc79ea97ef8ec0ffa1c63319830c6a326b`: no changes to the inspected test workflow/scripts.
* Shared [build-and-test workflow](https://github.com/GTNewHorizons/GTNH-Actions-Workflows/blob/be3d2919b6acfe52d441b42cb53cc71b340c68b0/.github/workflows/build-and-test.yml) and its report consumers at `be3d2919b6acfe52d441b42cb53cc71b340c68b0`.
* HeadlessNH [1.1.1 controller](https://github.com/GTNewHorizons/HeadlessNH/blob/35186b4de68b31d59a08c555e821902f1e5b3825/src/main/java/net/sxmaa/headlessnh/IntegrationTestController.java), pinned daily version, and current master `4eea6e6cfc095f690d18b1ef5aaf2bbe6f3ab07d` separately.
* lwjgl3ify 3.0.37 input/display sources at [`c87172bedb89256cebe5e4442dad896e1aadf482`](https://github.com/GTNewHorizons/lwjgl3ify/tree/c87172bedb89256cebe5e4442dad896e1aadf482). Its [mouse facade](https://github.com/GTNewHorizons/lwjgl3ify/blob/c87172bedb89256cebe5e4442dad896e1aadf482/src/main/java/org/lwjglx/input/Mouse.java) and [display facade](https://github.com/GTNewHorizons/lwjgl3ify/blob/c87172bedb89256cebe5e4442dad896e1aadf482/src/main/java/org/lwjglx/opengl/Display.java) explain native coordinate differences found in fullpack execution.
* Existing consumers: GT5's [EBF formation holder](https://github.com/GTNewHorizons/GT5-Unofficial/blob/5250820c5533582254f18f549503df1b9327e540/src/main/java/gttests/electricblastfurnace/ElectricBlastFurnaceFormationTests.java) at `5250820c5533582254f18f549503df1b9327e540` and AE2's [storage bus holder](https://github.com/GTNewHorizons/Applied-Energistics-2-Unofficial/blob/ab15e3a7259cbd9a7088138fade46e5f6f02ad18/src/main/java/appeng/gametests/automation/storagebus/StorageBusTests.java) at `ab15e3a7259cbd9a7088138fade46e5f6f02ad18` use ordinary server fixtures/assertions. Consumer-specific client targeting has no production adopter among these inspected holders.

## Requirements and ownership

The daily's [dual tests](https://github.com/GTNewHorizons/DreamAssemblerXXL/blob/28f22982e2666a00f5a335ec518d706d6b853259/scripts/tests/dual_tests.sh) invoke reported **server** tests over RCON. HeadlessNH keeps a client connected until the external serverloaded gate opens, then enters singleplayer. The final worldloaded marker currently triggers shutdown after five seconds. Neither client connectivity nor that marker establishes a GUI assertion.

Real client coverage includes native GUI/key/mouse handling, consumer widget hit testing, client state after packets, and rendered screenshots. Ordinary fixtures, recipes, and server assertions continue through HQA's existing dedicated-server runner. Client scenarios run in an integrated world: this exercises a real client, Forge, packets, containers, and authoritative server gameplay state in one process while allowing existing server-thread test methods to queue client work. Network-specific dedicated-server behavior still requires the existing separate-server run. Arbitrary test lambdas cannot cross processes: this implementation does not claim connected-dedicated-server GUI scenarios. No inspected production consumer currently uses such a client API; PR #259's client consumers are examples. GT and AE2's existing server holders remain compatible. Consumer-specific GUI targeting stays in consumer code.

HQA owns discovery, selectors, batch hooks, fixtures, sequences, assertions, bounded case cleanup, and reports. HeadlessNH owns menu navigation and world creation; launchers own argv, natives, a private display/game directory, deadlines, process groups, crash logs, and result retention. Consequently a packaged mod only needs HQA and its test holders in JARs, not a source checkout or Gradle wrapper.

Java 8 bytecode and APIs are required. The historical daily actually ran Java 8 with LWJGL 2.9.4 and Java 26 with lwjgl3ify 3.0.37 / LWJGL 3.4.3. Native AWT input uses the operating system event path and Minecraft's ordinary input loop, including Forge hooks, without replacing private input buffers. Linux automation uses a private Xvfb display; unsupported native input is an infrastructure error. Public framebuffer APIs work through the compatibility facade. Runtime failures and missing/inconsistent reports must never become optional successes.

## Reuse verdict

Adapting the whole PR would import two additional concerns: a Gradle supervisor that cannot launch an assembled pack, and a timing/report redesign (schema 3 to 5) unrelated to proving client behavior. Reimplementing every lifecycle seam would duplicate valuable existing work. **Reuse selected components on master.**

Retain the PR's annotation-metadata side filter, serial client work queue, asynchronous END sequence steps, asynchronous cleanup before server cleanup, and sequential reported client cases. Retain the existing result pipeline and schema 3. Fix cancellation, fatal-error propagation, cleanup deadlines, and late crash handling in those seams. Do not swallow a real game-loop crash as an optional test failure.

Discard private LWJGL input/window reflection: the packaged lwjgl3ify `Keyboard` has an event queue and no `readBuffer` or `keyDownBuffer`, and `Display.getImplementation()` returns null. The PR's input release and native resize therefore fail on the daily backend. Discard its scratch-world launcher, audio overrides, accelerated client/server timing, broad fluent facade, and new timing records. These do not establish a required consumer contract and increase the maintenance surface. Keep deferred, toolkit-independent target bounds and readiness predicates.

Use a small Python launcher/verifier for development and packaged commands. It creates a new report directory, supervises only its invocation's process group, requests bounded cancellation, and rejects missing, stale, malformed, incomplete, inconsistent JSON/XML, wrong selectors, forced termination, and crashes. The existing external exit semantics remain 0/1/2.

Position and focus the private native window explicitly. After geometry changes, correct the OS pointer from publicly observed coordinates and wait for rendered consumer hover again. This supports both LWJGL backends, including a one-pixel target at GUI scale 1, without backend-specific private fields. Fullpack examples allow bounded cold-frame rendering time; input completion still waits on state.

Reuse `GridSweeper.clearAndNotify` when preparing client-mode fixtures. The existing storage-only clear leaves terrain blocks stale on an already connected client: placing air afterward does not notify an unchanged server block. Fullpack execution exposed that failure through the actual rendered ray; fixture readiness now also waits for the client ray and held item before native use input.

## Downstream integration

The shared package workflow needs an opt-in client stage, a fresh isolated game directory, HeadlessNH singleplayer navigation, the client selector, and retained reports. The daily needs client HQA flags, a separate client report directory, `headlessnh.gate.worldloaded`, and a watcher that waits for HQA completion rather than closing at worldloaded. Its dedicated server retains its current flags and RCON phase. Existing report scripts check only status or exitCode; use the stricter verifier for both client and server reports. These downstream changes are supplied locally; they are not already deployed support.

HeadlessNH dismisses unsolicited pause menus. Scenarios that test the pause menu itself need another world launcher or a downstream HeadlessNH change. Rendering that depends on hardware or a particular shader requires a consumer-specific rendering environment and assertion; screenshots alone are diagnostics, not pixel correctness proof.

Local runtime evidence and external limitations are recorded in [Validation](client-testing-validation.md).
