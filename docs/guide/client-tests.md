---
title: Real client scenarios
description: Native input, integrated gameplay, owning threads, and packaged Linux automation.
---

# Real client scenarios

Use client scenarios when the behavior needs Minecraft's GUI/input loop, client state after packets,
or rendering. HQA runs them in an integrated world and reuses server fixtures, sequences, assertions,
batch hooks, selectors, and reports. Continue using the dedicated-server suite for server-only and
dedicated-network behavior. Connected-dedicated-server GUI execution is not implemented.

Install HQA and the consumer's test JARs in the normal client and use its existing launcher. Enable
client-test mode with the [documented JVM settings](../reference/jvm-flags.md#horizonqaclient); HQA waits for an
integrated world before running. HeadlessNH and the optional [CI tools](ci-tools.md) automate world
creation and process supervision. Mod authors do not copy launcher scripts into their repositories.

## Author a scenario

Separate client dependencies into a `@GameTestHolder(value = "mymod", clientOnly = true)` class. Tests
retain the `public static void method(GameTestHelper helper)` signature. Holder metadata is filtered
before class loading; dedicated discovery never links these classes.

```java
@GameTestHolder(value = "mymod", clientOnly = true)
public final class ClientTests {
    @GameTest(timeoutTicks = 200)
    public static void opensScreen(GameTestHelper helper) {
        ClientTest client = ClientTest.attach(helper);
        helper.startSequence()
            .thenExecuteAsync("open prerequisite screen", 40, () -> client.run(c -> {
                // Prepare the screen, or use normal input when opening it is under test.
                Minecraft.getMinecraft().displayGuiScreen(new MyScreen());
            }))
            .thenExecuteAsync("native button click", 60, () -> client.click(0, "submit", c -> {
                MyScreen screen = c.screen(MyScreen.class);
                MyButton button = screen.submitButton();
                return new ClickTarget(button, button.bounds(), button::isReady);
            }))
            .thenWaitUntilAsync("client observed response", 40,
                () -> client.run(c -> c.screen(MyScreen.class).assertResponse()))
            .thenWaitUntil("authoritative gameplay changed", 40, () -> {
                // Assert the real server state through helper, labels, and consumer APIs.
            })
            .thenSucceed();
    }
}
```

The screen/button names above belong to the consumer. HQA knows no GUI toolkit: a `ClickTarget` contains
identity, a rectangle in current scaled GUI pixels, and a readiness predicate. Resolve these anew on
the client thread; return `null` while absent and throw on ambiguity. HQA clips bounds, positions the
native pointer, waits for its position and rendered hover state, then presses and releases through
the operating system. The actual Minecraft/Forge handlers receive the events. Completion means input
was released; separately wait for the resulting GUI or authoritative server state.

`ClientTest.run` queues a callback at client END; `screen`, `afterTest`, `holdKey`, and `releaseKey` must
be called there. `key(KeyEvent.VK_*)` sends a native key press/release. `useItem()` uses the player's
configured use-item binding with no GUI; fixture setup may equip/aim the player, but must not call the
item/block handler. `capture(name)` saves a rendered framebuffer as PNG and encodes off the game thread.
Screenshots are diagnostics, not pixel correctness assertions.

Test bodies and ordinary sequence steps run on the integrated server thread. Queue client work with
`thenExecuteAsync` or `thenWaitUntilAsync`; never block either game thread or touch client state from a
server callback. Assert server inventory/recipe/world state through ordinary helper/sequence calls.
Both queues preserve START, world tick, END order. Test and step budgets count server END ticks;
slow rendering may need larger maximum budgets, while assertions still wait on observable state.
Client-mode fixture preparation notifies cleared blocks as well as placed blocks, so the client sees
air in an existing terrain chunk. Await synchronized fixture/player state and the rendered interaction
target before sending input; server placement alone does not establish client readiness.

## Cleanup and failures

Register restoration before mutation. Register client callbacks with `client.afterTest` inside
`client.run`, and server callbacks with `helper.afterTest`. `ClientTest.attach` registers one exclusive
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
configuration, cleanup, crash, cancellation, reporting and incomplete execution exit `2`. Schema `3`
is unchanged. Client state, pending target, and screenshot paths appear in existing JUnit system-out
and JSON output. Retain the complete report directory, including `launch.log`, `xvfb.log`,
`launch-result.json`, `artifacts/`, and game logs/crash reports.

## Automated development runs

Linux needs Python 3.9+, Xvfb, xdotool, and an AWT-capable Java runtime. Automation creates a private
display and a new game directory; native input is never sent to the developer's desktop. HeadlessNH
owns menu navigation and world creation. The examples include its development runtime dependency:

```groovy
runtimeOnlyNonPublishable('net.sxmaa.headlessnh:HeadlessNH:1.1.1:dev')
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

For consumer repositories, CI maintainers acquire the single versioned archive centrally; local
developers can run that same artifact against their existing Gradle task:

```bash
python3 hqa-tools.pyz run-client --gradle-task runClient \
  --tests mymod:ClientTests --report-dir "$PWD/build/client-run"
```

The archive contains and extracts its isolation adapter automatically. No adjacent helper files are
required. See [CI tooling](ci-tools.md) for artifact coordinates, task options, and verification.

`ClientInteractionTests` verifies a one-pixel live target through a GUI scale change at odd display dimensions, Escape through
normal keyboard handling, and a real chest GUI with server inventory/cursor assertions. Its final case
checks that the following session sees released input, a closed screen, restored GUI scale, and the
original player inventory, cursor, selected slot, and game mode. It does not replay the first GUI test.
Run the whole holder to exercise those checks across successive tests.
`ClientFailureTests` intentionally tests required/optional assertions, held input on timeout,
cancellation, a rendering crash, world unloading, and cleanup. Select its cases explicitly; its
namespace is not a passing smoke suite.

## Packaged and CI launch

Put the reobfuscated HQA and consumer test JARs plus HeadlessNH in an isolated assembled pack. Use the
pack's normal bootstrapper to prepare libraries, natives, and an argv file: one literal argument per
line, starting with an absolute Java executable. No source checkout or Gradle wrapper is required.
The game directory must contain no `saves` directory. For example, after Prism bootstrap:

```bash
python3 /path/to/hqa-tools.pyz run-client \
  --cwd /path/to/isolated-pack/.minecraft \
  --argv-file /path/to/isolated-pack/launch.argv \
  --env-file /path/to/isolated-pack/launch.env \
  --tests mymod:ClientTests \
  --report-dir /path/to/results/client --timeout 900
```

Launch environment assignments are parsed as literal values, without shell evaluation. The launcher's
default HeadlessNH singleplayer mode holds `gate.worldloaded` while HQA executes and HQA shuts down
after writing its result. `--headless existing` preserves a prepared launcher's navigation settings
and still installs the worldloaded gate. Extra arguments after `--` are forwarded to Gradle, or
inserted as JVM arguments in a packaged launch. Use Java 8/LWJGL 2 and the pack's supported modern
runtime/lwjgl3ify variant with the same test/API: input does not reflect into LWJGL private buffers.

Package CI should opt into a separate client stage after build and its server tests. Daily CI should
keep the existing dedicated RCON tests, then let its combined HeadlessNH client reach singleplayer
and run client holders there. Replace the current five-second worldloaded shutdown with waiting for
HQA's report/process completion. Give server and client distinct report directories; verify and
retain both. Concrete repository-specific steps are in [Downstream integration](../contributing/client-ci-integration.md).

HeadlessNH 1.1.1 dismisses unsolicited pause menus. Pause-menu tests need another world launcher or a
HeadlessNH change. Hardware/shader-specific rendering also needs a consumer-defined environment and
assertion. These are external integration requirements, not implemented HQA coverage.
