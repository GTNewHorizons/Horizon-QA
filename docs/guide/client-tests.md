---
title: Real client scenarios
description: Native input, integrated gameplay, owning threads, and packaged Linux automation.
---

# Real client scenarios

Use client scenarios to test Minecraft's GUI/input loop, client state after packets, or rendering.
Install HQA and your test JARs in the client, then enable
[client-test mode](../reference/jvm-flags.md#horizonqaclient). Create a singleplayer world manually or
use HeadlessNH and the [CI tools](ci-tools.md) to automate world creation.

## Execution model

Minecraft starts its integrated server when you enter singleplayer. The client and integrated server
run in the same game JVM, with separate owning threads. HQA waits for a world, player, and rendered
world frame before starting the reported test run.

Test methods, fixtures, batch hooks, and server assertions run on the integrated server thread.
Client callbacks, native input, and framebuffer capture run on the client thread. The sequence awaits
queued client operations without blocking either game thread. Gameplay actions still pass through
Minecraft and Forge's normal input and packet handling before changing authoritative server state.

Client scenarios use the usual discovery, selectors, fixtures, assertions, cleanup, and reports.
Run server-only and dedicated-network tests on a dedicated server. Client scenarios require an
integrated world; running them while connected to a dedicated server is unsupported.

## Author a scenario

Separate client dependencies into a `@GameTestHolder(value = "mymod", clientOnly = true)` class. Tests
retain the `public static void method(GameTestHelper helper)` signature. Holder metadata is filtered
before class loading; dedicated discovery never links these classes.

```java
@GameTestHolder(value = "mymod", clientOnly = true)
public final class ClientTests {
    private static final ClientTarget SUBMIT = ClientTarget.of("submit", c -> {
        MyButton button = c.screen(MyScreen.class).submitButton();
        return new ClickTarget(button, button.bounds(), button::isReady);
    });

    @GameTest(timeoutTicks = 200)
    public static void opensScreen(GameTestHelper helper) {
        ClientTest.scenario(helper)
            .defaultTimeoutTicks(40)
            .client("open prerequisite screen", c -> {
                // Prepare the screen, or use normal input when opening it is under test.
                Minecraft.getMinecraft().displayGuiScreen(new MyScreen());
            })
            .awaitScreen(MyScreen.class)
            .click(SUBMIT, 60)
            .awaitClient("client observed response", c -> c.screen(MyScreen.class).assertResponse())
            .awaitServer("authoritative gameplay changed", () -> Checks.assertSubmitted(helper))
            .capture("submitted")
            .succeed();
    }
}
```

The screen/button names and `Checks.assertSubmitted` above belong to the consumer; the latter asserts
the real authoritative gameplay result through helper labels and consumer APIs. Import `ClientTest`, `ClientTarget`, and
`ClickTarget` from `com.gtnewhorizons.horizonqa.api.client`. `ClientTarget.of` names a deferred resolver;
creating or reusing the target does not query a screen. HQA knows no GUI toolkit: a `ClickTarget` contains
identity, a rectangle in current scaled GUI pixels, and a readiness predicate. Resolve these anew on
the client thread; return `null` while absent and throw on ambiguity. HQA clips bounds, positions the
native pointer, waits for its position and rendered hover state, then presses and releases through
the operating system. The actual Minecraft/Forge handlers receive the events. Completion means input
was released; separately wait for the resulting GUI or authoritative server state.

## Fluent contract

`ClientTest.scenario(helper)` attaches the client lifetime and creates the test's
`GameTestSequence`. Build the chain on the server test thread and finish with `succeed()`; do not also
call `helper.startSequence()`. Methods register deferred work. They do not run input during authoring.

| Operation | Contract |
|---|---|
| `client(label, callback)` | Run the callback once at client END |
| `server(label, callback)` | Run once at server END; synchronous and nonblocking |
| `awaitClient(label, assertion)` | Retry completed client assertions, at most one attempt in flight |
| `awaitServer(label, assertion)` | Poll authoritative server assertions at END after the world tick |
| `awaitScreen(type)` | Poll the live screen type on the client thread |
| `click(target)`, `rightClick(target)` | Await a live target, send one normal native click and observe release |
| `shiftClick(target)` | Own Shift around one native left-click and observe both mouse and modifier release |
| `useHeldItem()` | Send the configured use-item binding with no GUI; fixture setup may equip/aim the player |
| `key(KeyEvent.VK_*)`, `escape()` | Send one native physical key press/release; this does not promise text entry |
| `capture(name)` | Capture a completed framebuffer and encode its PNG off the game thread |

Actions are never retried because a later observation fails. An assertion wait retries only completed
assertion failures; other exceptions fail immediately with their original cause. Observation callbacks
must avoid mutation. Click completion establishes released input; separately await the expected client
and authoritative server state. Screenshots are diagnostics, not pixel correctness assertions.

Bounded operations initially allow 100 server END ticks, including time in flight.
`defaultTimeoutTicks(40)` changes the budget for subsequently registered steps. An exceptional budget
belongs directly to its operation: `click(SUBMIT, 60)`, `awaitScreen(MyScreen.class, 80)`, or
`awaitClient("reply", 80, assertion)`. The same overload pattern applies to other gestures, `client`,
`awaitServer`, `capture`, and `async`. A custom callback's label belongs directly to that step; gestures
derive labels from their named target, key or checkpoint. Options never spill into adjacent operations.
The enclosing `@GameTest(timeoutTicks = ...)` still bounds the whole test. Synchronous `server` callbacks
have no tick budget and must return promptly.

Shift-click rejects an already-held Shift key so it cannot release a caller's modifier. Target-resolution
or click errors schedule modifier release before the operation completes; timeout and cancellation
invalidate queued work and use bounded session teardown. For a runnable gesture-recovery example,
see `ClientInteractionTests.aNativeButton` in the [examples mod](../contributing/examples-mod.md).

## Custom operations and owning threads

`async(label, maxTicks, c -> completionStage)` starts its supplier on the **server** thread. Use queued
session methods there, such as `c.click(...)` or `c.run(callback)`; never read client state directly in
that supplier. Neither game thread may block on `get` or `join`. `serverSequence()` exposes the same
sequence for advanced START/END scheduling; finish those registrations before continuing the chain.
Custom mutations still register restoration before changing state:

```java
ClientTest.scenario(helper)
    .client("temporary GUI scale", c -> {
        Minecraft mc = Minecraft.getMinecraft();
        int original = mc.gameSettings.guiScale;
        c.afterTest(() -> mc.gameSettings.guiScale = original);
        mc.gameSettings.guiScale = 2;
    })
    .succeed();
```

For lower-level authoring, use `ClientTest.attach(helper)` with `thenExecuteAsync` and
`thenWaitUntilAsync`. `ClientTest.run` queues a callback at client END; `screen`, `afterTest`, `holdKey`,
and `releaseKey` must be called there. `useItem()` is the queued counterpart of fluent `useHeldItem()`.
Both authoring styles use the same native input, sequence, result and cleanup implementation.

Test bodies and ordinary sequence steps run on the integrated server thread. The fluent interface
queues client work for you; never touch client state from a server callback. Assert server
inventory/recipe/world state through `awaitServer` or ordinary helper/sequence calls.
Both queues preserve START, world tick, END order. Test and step budgets count server END ticks;
slow rendering may need larger maximum budgets, while assertions still wait on observable state.
Await synchronized fixture/player state and the rendered interaction target before sending input;
server placement alone does not establish client readiness.

## Cleanup and failures

Register restoration before mutation. Register client callbacks with `c.afterTest` inside a fluent
`client` callback (or `ClientTest.run`), and server callbacks with `helper.afterTest`.
`ClientTest.scenario` and `ClientTest.attach` register one exclusive
asynchronous teardown. It invalidates pending work, releases owned native input, waits for release
through the game loop, captures the final frame, restores client state/screen and an ungrabbed GUI
pointer, then permits server cleanup. Minecraft owns the grabbed pointer when returning to gameplay.
Cases run sequentially. Failed or unfinished cleanup aborts remaining cases.

The teardown budget is 100 END ticks; final client shutdown also has a five-second wall-clock budget.
The runtime waits up to 600 seconds for the first rendered integrated world/player; no bootstrap time
consumes test ticks. The external launcher bounds the entire launch and handles a stalled/crashed JVM.
Writing `stop-client` in the report directory requests cancellation. Disconnect/world loss and real
client/server loop crashes are infrastructure failures even for optional cases. Input release and
restoration are best effort after a fatal JVM failure; the launcher retains logs and owned-JVM thread
dumps, then terminates its own process group within a bounded grace period.

Required assertions/timeouts exit `1`; optional assertions/timeouts remain exit `0` and JUnit-skipped;
configuration, cleanup, crash, cancellation, reporting and incomplete execution exit `2`. Results use
[status schema `3`](../reference/jvm-flags.md#status-json). Client diagnostics and screenshot paths
appear in JUnit system-out and JSON output. Retain the complete report directory, including `launch.log`, `xvfb.log`,
`launch-result.json`, `artifacts/`, and game logs/crash reports.

## Visible local runs

Launch the examples through the normal Java 8 client task to watch the native input and gameplay.
Use a fresh game/report directory and create a new singleplayer world manually:

```bash
mkdir -p "$PWD/build/horizonqa"
demo_dir="$(mktemp -d "$PWD/build/horizonqa/visible-XXXXXX")"
mkdir "$demo_dir/game"

timeout --signal=TERM --kill-after=15s 900s ./gradlew --no-daemon :examples:runClient \
  -PrunClientWorkingDirectory="$demo_dir/game" \
  --mcJvmArgs=-Dhorizonqa.mode=ci \
  --mcJvmArgs=-Dhorizonqa.client=true \
  --mcJvmArgs=-Dhorizonqa.world=normal \
  --mcJvmArgs=-Dhorizonqa.tests=horizonqaexamples:ClientInteractionTests \
  --mcJvmArgs="-Dhorizonqa.reportDir=$demo_dir" \
  --mcJvmArgs=-Djava.awt.headless=false \
  --mcArgs=--width --mcArgs=959 \
  --mcArgs=--height --mcArgs=541
```

Create a Creative/Superflat world within the 600-second startup budget. Keep the game window focused
and let the tests move the pointer and press keys. They start after the first ordinary world frame,
write reports into `$demo_dir`, and close the client when finished. This command uses the desktop
display; Python, Xvfb and xdotool are not required. The Linux `timeout` command bounds the launch,
and `--no-daemon` gives this invocation its own Gradle process.

The examples omit HeadlessNH from ordinary development launches. The automation tools enable its
menu navigation and world creation with the Gradle property `-PhqaHeadlessNH=true`.

## Automated development runs

Linux needs Python 3.9+, Xvfb, xdotool, and an AWT-capable Java runtime. Automation creates a private
display and a new game directory; native input is never sent to the developer's desktop. HeadlessNH
owns menu navigation and world creation. The examples opt into its development runtime dependency
when the tools pass `-PhqaHeadlessNH=true`:

```groovy
if (providers.gradleProperty('hqaHeadlessNH').getOrElse('false').toBoolean()) {
    runtimeOnlyNonPublishable('net.sxmaa.headlessnh:HeadlessNH:1.1.1:dev')
}
```

From the Horizon-QA repository root, the shortcut builds the tools archive and runs the focused suite:

```bash
./gradlew hqaClientTest
```

The supervisor forwards every `horizonqa.*` property through its own `--mcJvmArgs`, creates a private
959×541 display, positions the real native window at `(0, 0)`, focuses it, and requires fresh matching
JSON/JUnit. The shortcut chooses a new report directory under `build/horizonqa/` each time. Set
`-PhqaTests=<selector>` for a focused case or `-PhqaClientTask=:examples:runClient25` for a supported
modern runtime. An explicit `-PhqaReportDir=<path>` must be new.

In your mod's repository, run the tools archive against its client task:

```bash
python3 hqa-tools.pyz run-client --gradle-task runClient \
  --tests mymod:ClientTests --report-dir "$PWD/build/client-run"
```

See [CI tooling](ci-tools.md) for artifact coordinates, launch options, and report verification.

`ClientInteractionTests` demonstrates native button input, Escape, chest inventory changes, and
restoration between successive tests. Run the whole holder to exercise that lifecycle.
`ClientFailureTests` demonstrates required/optional failures, timeout, cancellation, crashes, world
unloading, and cleanup. Select its cases explicitly: some intentionally fail or terminate the run.

## Packaged and CI launch

Install the reobfuscated HQA and test JARs in an isolated pack. The same test classes run without a
source checkout or Gradle wrapper. Use the pack's launcher and a compatible Java/LWJGL runtime;
modern Java requires the pack's supported lwjgl3ify configuration. Automated world creation also
requires HeadlessNH.

Follow [packaged launch setup](ci-tools.md#existing-packaged-launchers) to use a prepared Java command,
or the [existing-launcher contract](ci-tools.md#integrate-with-an-existing-launcher) to add HQA to your
own CI lifecycle. Give server and client suites separate report directories and retain both.

Screenshots are diagnostics. Tests for a specific shader or graphics device must provide their own
environment and correctness assertions. HeadlessNH 1.1.1 dismisses unsolicited pause menus; use a
launcher without those menu overrides when testing pause-menu input. Client-test mode keeps the
integrated world ticking while a GUI is open and disables pause on focus loss, so it does not test
Minecraft's normal pause semantics.

## Diagnose a client run

Start with `horizonqa-result.json` and the failing JUnit case's `system-out`. Look for `lastScreen`,
`waitingFor`, and `screenshot` diagnostics. For an automated launch, also inspect
`launch-result.json` and `launch.log`. A crash before HQA initializes can leave no game reports;
the launch log and JVM crash report are then the starting point.

| Symptom | What to check |
|---|---|
| `CLIENT_CONFIG` | Client mode requires `mode=ci`, `world=normal`, `autoRun=true`, `stopServer=true`, and `turbo=1` |
| `CLIENT_STARTUP` or no tests start | Enter a singleplayer world, close loading/menu screens, and reach a rendered player/world within the 600-second startup budget |
| Native input or focus error | Keep the visible window focused; automation needs a working private display, xdotool, and an AWT-capable JVM with `java.awt.headless=false` |
| Timeout while resolving a target | Read `waitingFor` and the named step; check screen type, live bounds, readiness, and synchronized fixture/player state |
| Cleanup error and subsequent cases do not start | Read the original cleanup cause; register restoration before mutation and let queued input release complete |
| `Failed to create HeadlessNH marker` with HeadlessNH 1.1.1 | Its disabled mode repeats world-marker writes; omit HeadlessNH for manual world creation, as the examples do |
| Missing or inconsistent reports | Treat the run as infrastructure failure; check the launcher log and verify a fresh report directory with the [CI tools](ci-tools.md#verify-reports) |

To reproduce one case, set `-PhqaTests=<exact-test-id>` on `hqaClientTest`, or pass
`--mcJvmArgs=-Dhorizonqa.tests=<exact-test-id>` to the visible client task. Use a fresh game/report
directory, then inspect the retained screenshots and logs. Increase a step's maximum budget only
when its readiness condition is correct and the environment needs more rendering time.
