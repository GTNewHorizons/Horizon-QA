---
title: Client tests
description: Author real-client scenarios with live targets, native input, multiple windows and bounded cleanup.
---

# Client tests

Client tests run a real Minecraft 1.7.10 client and integrated server. They need a working graphics context. Linux CI uses Xvfb, Mesa software OpenGL and the `xrandr` executable from `x11-xserver-utils`. LWJGL 2 invokes that executable to discover display modes. The launcher runs through Gradle on Windows and Linux, with JDK 17 or newer for build-side process supervision and Python 3.9+ for report verification. The default LWJGL 2 profile runs Minecraft on Java 8. An SDL lwjgl3ify profile uses the Java version required by that runtime.

Use `@GameTestHolder(value = "mymod", clientOnly = true)` for a holder that requires client classes. Its methods still receive `GameTestHelper` and run on the server thread. An integrated client can also execute ordinary holders without wrappers. Select their original test IDs to retain parameters, templates, batches and hooks. Normal server discovery filters client-only holders from Forge ASM metadata before class loading. Explicitly selecting an unavailable client test on a dedicated server produces a selection error, not a skipped pass.

Automatic bootstrap waits for Forge mod loading to complete and starts its scratch world from the client tick, independently of the main menu's class. Packs with a custom menu do not need to replace that menu or supply a bootstrap addon. The scratch overworld remains flat with seed `0`, under the `normal` world profile. Registered mod dimensions still follow their ordinary server-start lifecycle. `ClientBootstrapTests.commonTestStartsFromCustomMenu` supplies a custom screen and verifies that the original common test ID executes.

## Write a client scenario

Use the [portable client tools](client-tools.md#one-unattended-command) to launch scenarios with bounded process ownership and verified reports. The [client API reference](../reference/client-api.md) provides method lookup. [CI and JUnit reports](ci.md) defines selectors, reports and exit codes.

Use `ClientTest.scenario(helper)` to build the test as one ordered chain:

```java
ClientTarget next = ClientTarget.of("details.next", window -> findNextButton(window));

ClientTest.scenario(helper)
    .useHeldItem()
    .awaitScreen(MyScreen.class)
    .click(next)
    .awaitScreen(DetailsScreen.class)
    .capture("details")
    .escape()
    .succeed();
```

The target resolver receives the `ClientWindow` being observed. Scenario gestures use the session's main window by default. The screen types and `findNextButton` belong to the consumer. The lookup returns a live `ClickTarget`, including visible bounds and actual hit-test readiness. Creating a `ClientTarget` stores its description and resolver without querying the GUI. It can be reused across steps even when the screen or layout changes.

Scenario methods register deferred steps on the existing server-owned `GameTestSequence`. Building the chain does not perform input. It creates one client session and one sequence, so do not also call `attach(helper)` or `helper.startSequence()` for that test. Finish registration with `succeed()`.

## Automated session behavior

The launcher initializes the fresh game's audio settings with master volume at 50%, music at 0% and weather at 0%. Other sound categories keep Minecraft's defaults. These settings belong to the isolated game directory.

Automatic clients are fully silent by default. To use configured sound levels, pass `--gradle-argument=--mcJvmArgs=-Dhorizonqa.client.sound=true` to the portable launcher. With `runClient` directly, use `--mcJvmArgs=-Dhorizonqa.client.sound=true`. The mute overrides effective sound levels from startup without changing saved audio options. It has no effect on a normal interactive client.

Automatic client sessions discard physical keyboard input and mouse movement, clicks and wheel input before they reach the game's event queues and polled state. Synthetic HQA keys, text, clicks, drags and scrolling continue through the usual game handlers. Native window controls and operating-system shortcuts are not blocked. Manual laboratory gameplay remains normally controllable. `ClientIsolationTests` exercises the LWJGL2 devices with noisy native input sources and verifies that synthetic presses and releases survive polling and that restoring native input removes the isolation. `ClientSdlIsolationTests` supplies SDL events and native polled key state to verify isolation under lwjgl3ify.

Saving options during a test preserves configured sound levels. Finishing the run removes the in-memory mute override. Setting `horizonqa.client.sound=true` uses the configured level for every sound category.

Clients use process-local LWJGL input, so inactive windows do not compete for the OS pointer. Keep their windows rendering. Fully minimized windows may stop rendering with some renderer mods, which prevents frame-dependent steps from completing. Parallel launches also need enough memory and GPU capacity for each Minecraft process.

The underlying JVM opt-in is `-Dhorizonqa.client=true` together with `-Dhorizonqa.mode=ci` and `-Dhorizonqa.world=normal`. Each property must use its own `--mcJvmArgs`. Existing test selectors apply. Auto-run and shutdown must be enabled and turbo must be 1. The local client bootstrap uses a flat creative world, so void-world overrides and server turbo are rejected. No matching tests is an error even when `horizonqa.allowNoTests=true`.

Bootstrap creates a new `horizonqa-<UUID>` scratch save under the launcher's client working directory. It never chooses an existing save. One launch reuses this world and client across the selected scenarios. Scenarios execute serially within their existing batch hooks. Bootstrap requests GUI scale 1, but GUI libraries may apply their own scale, so resolve targets using the active screen's dimensions and live widget geometry. Test mode suppresses the renderer's automatic focus-loss pause without changing the focus-pause setting, so closing a screen cannot reopen the pause menu just because the window is inactive. Test mode also suppresses native cursor grabbing. The client world and integrated server keep ticking and synchronizing even while an explicitly opened pausing screen is visible. The screen remains usable and is not automatically dismissed. The original GUI scale is restored during orderly shutdown. Scratch saves remain under the launch game directory for inspection.

`ClientPauseTests.inactiveWindowAllowsWorldInputAfterScreenCleanup` exercises an inactive game window with `pauseOnLostFocus=true`, real book input after closing a screen, and explicit pause-menu handling through Escape. Keep the game window visible and focus another application when running this regression. It fails its precondition if the game stays active.

The reported run starts after the first world frame finishes rendering. World/player creation alone does not mark the client ready, so initial rendering does not consume the first scenario's execution or cleanup budget.

## Server and client operations

Mix server fixture work, native client input and client assertions in the same scenario:

```java
@GameTestHolder(value = "mymod", clientOnly = true)
public final class ScreenTests {
    @GameTest(template = "screen_fixture", timeoutTicks = 400)
    public static void openAndReturn(GameTestHelper helper) {
        ClientTarget next = ClientTarget.of("details.next", window -> findNextButton(window));
        ClientTest.scenario(helper)
            .server("prepare server fixture", () -> {
                helper.setBlock("marker", Blocks.gold_block);
            })
            .afterTest(c -> removeTestOwnedClientState())
            .client("open fixture screen", c -> {
                Minecraft.getMinecraft().displayGuiScreen(new MyScreen());
            })
            .click(next)
            .awaitScreen(DetailsScreen.class)
            .capture("details")
            .escape()
            .server("verify server state", () -> {
                helper.assertBlockPresent(Blocks.gold_block, "marker");
            })
            .succeed();
    }
}
```

The screen types and lookup methods above belong to the consumer. Opening a supplied fixture screen is appropriate for a framework input test. Product scenarios should enter their screen through normal gameplay, for example with `useHeldItem()`. The runnable framework examples are in `examples`.

`ClientScenario`, `ClientTarget` and `ClientTest` live in `com.gtnewhorizons.horizonqa.api.client`. Custom `client` actions execute at client END, and completion is consumed at server END. `server` actions execute on the server at END. Do not capture live server worlds or tile entities in a client action. Pass immutable values between the two sides and perform server assertions in `server` or `awaitServer`.

See the [client API reference](../reference/client-api.md#scenario-methods) for these method contracts.

### Budgets and step descriptions

Client operations and assertion waits default to **100 test ticks per step**. The test's `@GameTest(timeoutTicks = ...)` still bounds the whole scenario. Native operations generate descriptions from the action and target name.

```java
ClientTest.scenario(helper)
    .defaultTimeoutTicks(80)
    .step("save the edited recipe")
    .withinTicks(160)
    .click(save)
    .awaitClient("confirmation is visible", c -> assertConfirmation(c))
    .succeed();
```

`step(label)` overrides only the next step's description, including an explicit custom label. `withinTicks(n)` overrides only the next bounded step's budget. In this example the click has 160 ticks, and the assertion returns to the 80-tick default. Budgets must be positive. A synchronous `server` action has no tick budget, so placing `withinTicks` before it is rejected. Consume pending options before `succeed()` or `serverSequence()`.

### Accelerating server-state waits

Use an explicitly accelerated wait for long server-owned work such as construction or smelting:

```java
scenario.withinTicks(1200)
    .awaitServerAccelerated("construction completed", 10, () -> assertConstructionComplete())
    .awaitClient("completed module visible", c -> assertModuleVisible(c))
    .capture("completed");
```

The multiplier is between 1 and 100, bounded by `HorizonQAProperties.MAX_TURBO_MULTIPLIER`. It requests that many full server ticks per normal server-loop iteration while the assertion is pending. World simulation, mod lifecycle callbacks and server test callbacks all run normally for each tick. It does not skip construction, change world time directly or accelerate client rendering. Actual speed depends on server workload.

The assertion runs at server END and must only observe server state. Do not queue client operations or wait for client synchronization inside it. Follow it with a normal client wait to observe the result on screen. The step budget and whole-test timeout still count simulated server ticks, so size them for the real gameplay duration.

The request belongs to the currently running sequence step. Success, failure, timeout, cancellation and cleanup remove its effect. The server loop checks the effective rate after every full tick before continuing its burst. Subsequent input and render steps run at the normal rate. If multiple tests share a server, acceleration is limited by the slowest active test, including cleanup. Ordinary scenarios remain at 1x.

Scoped acceleration applies to CI batches. Keep the global `horizonqa.turbo=1` for client launches. Dedicated-server batches may still use global turbo, which takes precedence over individual step requests. `ClientTurboTests` compares the wall time of real furnace smelting at 1x and 10x, verifies normal-rate rendering afterward and checks cleanup after an intentional optional timeout. Its timing regression expects 10x to finish in less than half the baseline duration, allowing substantial headroom without requiring an exact speed ratio.

### Custom asynchronous operations

Use `client` and `awaitClient` for client observations and assertions. Use `async` when an operation already returns a completion stage or needs a specialized composition:

```java
scenario.async("verify a rejected input", c -> c.useHeldItem().handle((ignored, error) -> {
    assertExpectedRejection(error);
    return null;
}));
scenario.awaitAsync("external result is available", c -> checkExternalResultAsync());
```

The `async` and `awaitAsync` callbacks themselves begin on the **server thread**. Queue client work through the supplied `ClientTest`, such as `c.run(...)` or its input methods. Do not read Minecraft client state directly in these callbacks.

Advanced server scheduling uses the same sequence:

```java
var scenario = ClientTest.scenario(helper);
scenario.serverSequence()
    .thenExecuteAtStart(() -> supplyFixtureInput());
scenario.useHeldItem()
    .awaitServer("operation completed", () -> assertServerResult())
    .succeed();
```

Finish registering raw sequence steps before returning to the fluent chain. Existing START/END ordering rules still apply. There is no second executor or separate cleanup lifecycle.

## Low-level client operations

`ClientTest.attach(helper)` remains available when authoring directly with `GameTestSequence`. Attach at most one session per test. The fluent scenario uses this same session internally. Low-level methods are also available through custom callbacks for specialized input and future composition. They are an advanced API, not a deprecated compatibility layer.

`run(Consumer<ClientTest>)` returns `CompletableFuture<Void>` and queues work at client END. Its completion is consumed by the sequence on server END.

See the [client API reference](../reference/client-api.md#low-level-main-window-operations) for these method contracts.

Click once, then wait for the resulting state in a separate step. Async waits retry only completed `AssertionError`s. An operation still in flight is never resubmitted. Unexpected exceptions fail with their original cause. Step budgets include time spent waiting for client work. Never block either game thread with `join`, `get`, sleeps, or manual polling loops. Completion callbacks run on their completing thread, so use another sequence step for server work.

GUI libraries can update hit-test caches on a clock separate from rendering. For those controls, pass a `ready` predicate based on the library's public observable state. For example, a ModularUI consumer can check whether its panel considers the uniquely located target widget below the mouse. False keeps the click pending without dispatching input. A thrown exception fails immediately. The enclosing sequence's tick budget bounds this readiness wait, and diagnostics retain the pending point. No extra frame count or fixed delay is required.

## Input and rendering

Use `scenario.resizeWindow(width, height)` for a real window resize. Dimensions are positive pixels. The operation requires a created, resizable, windowed LWJGL Display without an AWT parent. It requests native resizing of the existing window and waits for both LWJGL and Minecraft to observe the dimensions, followed by a normal completed frame. Its time budget comes from the existing scenario step.

Do not use `Display.setDisplayMode` as a substitute. LWJGL 2 recreates the window for that call, and its Linux backend may never report a resize event when the new window already has the target dimensions. Horizon's narrow LWJGL adapter calls the backend's native `reshape` path under the library's display lock. It does not call `Minecraft.resize`, set resize flags or manually poll display events. Unsupported library layouts fail explicitly.

The first resize records the session's original dimensions. Client teardown restores them through the same native path and waits for Minecraft and a frame before releasing the session. This runs even when a later test step fails. Remove manual `setDisplayMode` cleanup for scenarios using this operation. Custom code can await `ClientTest.resizeWindow(width, height)` through `scenario.async`. Do not enqueue it from a cleanup callback because teardown already owns restoration and ordinary operation submission is closed. `ClientResizeTests.resizesExistingWindowAndRestoresDimensions` exercises a round trip while preserving the active screen instance.

For a race fixture that must resize during an already pending click, use `ClientTest.requestWindowResize(width, height)` from an active client-thread callback. It requests the same native resize immediately and retains automatic restoration, but does not wait for the event or a frame. Calling it from the server thread or a closed session fails. The target resolver and readiness predicate must only observe state. Queueing `resizeWindow` from a resolver and waiting for its future deadlocks the FIFO queue because the active click stays ahead of the resize.

ClientTest has no separate render-callback registry. Register a one-shot `TickEvent.RenderTickEvent` listener on `FMLCommonHandler.instance().bus()` from a `scenario.client` setup action and unregister it with `c.afterTest(() -> bus.unregister(listener))`. At render END, observe that the target has been positioned, request the resize once, and let normal window events update the game. Keep click readiness false until that request has been sent and the new dimensions and actual hit test are ready. This exercises resize between positioning and dispatch without changing the queue's serial execution.

The default input backend is LWJGL 2.9. Horizon supplies both queued events and polled button/key state, plus event and polled cursor coordinates. With the supported SDL lwjgl3ify runtime installed, Horizon instead sends SDL events and maintains the shim state needed by the main window. A click waits for a normal rendered frame at the target before pressing, so libraries that record hover coordinates during rendering observe the new pointer. Normal `GuiScreen.handleInput` dispatch invokes the screen lifecycle. There is no OS mouse automation and no direct widget business callback. Unsupported LWJGL layouts fail explicitly. Complete clicks, Shift-clicks, scrolling, straight drags and individual keys with typed characters are supported. Whole-string entry and arbitrary held-key gestures are outside this version.

## Multiple windows and optional Offscreen support

Use `ClientTest.mainWindow()` for the Minecraft window and `ClientTest.offscreenWindow(int windowId)` for an existing Offscreen window. Both return `ClientWindow`. Bind an additional window on the client thread after the consumer has opened it and obtained its SDL window ID. A handle retains that window's identity. It does not silently switch to another window after closure.

Offscreen is optional. Ordinary client and dedicated-server runs do not require it. The additional-window backend targets Offscreen 0.3 with the SDL runtime from lwjgl3ify 3.0.25. A profile using it must supply those runtime mods, their native libraries and the compatible Java runtime. HQA does not install Offscreen or change the consumer's runtime profile. A missing or incompatible integration fails when the test tries to bind it.

All windows belong to the same client session and use its existing ordered queue. Use `scenario.async` to await window operations and `scenario.awaitAsync` to retry observable assertions. Those callbacks begin on the server thread. Read GUI state only inside `ClientWindow.run`, a target resolver or another queued client operation.

A named target can be reused by the scenario's main window and an already bound `ClientWindow extraWindow`:

```java
ClientTarget save = ClientTarget.of("editor.save",
    window -> findSaveButton(window.screen()));
scenario.click(save)
    .async("save from the auxiliary editor", client -> extraWindow.click(0, save));
```

`findSaveButton` is a consumer lookup returning a live `ClickTarget`. Each lookup receives the window being operated on. A target does not retain the main screen or a particular window handle.

See the [client API reference](../reference/client-api.md#window-operations) for these method contracts.

Offscreen input enters SDL's routing filter with the selected window ID. HQA does not invoke button actions directly. The Offscreen capture hook reads that window's completed framebuffer. A frame rendered by the main game does not satisfy an additional window's frame wait. Use distinct checkpoint names when comparing windows in the report.

Input completion alone does not prove that an additional window consumed an event or changed gameplay state. Follow input with `awaitAsync` and a queued assertion on the expected view, then assert server state separately when the operation changes gameplay. Keep semantic target lookup scoped to the bound window. Do not use `Minecraft.currentScreen` to find an additional window's controls.

Open only fixtures owned by the test and register their cleanup during the same client callback as creation. `offscreenWindow(id)` binds an existing window but does not create it or establish that the test owns it. Explicit `close()` is an operation to await in the scenario. Cleanup callbacks must close their owned fixture through the consumer's synchronous API because ordinary queue submission is closed during teardown. Main-window resize retains its normal automatic restoration. Close test-owned additional windows rather than leaving resized windows behind.

The runnable example is `horizonqaexamples:ClientWindowTests.offscreenWindowKeepsMainUsable`. It opens two real `GuiScreen` instances with separate button counters, clicks and captures both, resizes and clicks the Offscreen view, closes it and verifies that the original main screen still accepts clicks. It skips broad selections to avoid opening an unexpected native window. Select the exact ID, either alone or in a comma-separated list of exact IDs. Selecting it without Offscreen fails with a prerequisite diagnostic.

The exact-ID examples `horizonqaexamples:ClientWindowTests.offscreenClickFailureIsReported` and `horizonqaexamples:ClientWindowTests.offscreenKeyFailureIsReported` intentionally change fixture state and then throw from a hosted handler. Each must produce an optional failure retaining the original exception, not a passed test. The launcher still exits zero for optional failures, so inspect the case status and exception in the report when verifying these examples.

Supply an Offscreen 0.3 alpha dev JAR and select the example's opt-in Java 21 SDL profile:

```sh
./gradlew -p scripts/client-tests runClientTests \
    --client-task :examples:runClient17 \
    --tests horizonqaexamples:ClientWindowTests.offscreenWindowKeepsMainUsable \
    --gradle-argument=-PoffscreenJar=/absolute/path/to/offscreen-0.3-alpha-dev.jar
```

The `offscreenJar` property adds that local file only to the examples runtime, pins lwjgl3ify 3.0.25 across its normal and patch dependencies, and selects Java 21 without hotswap for `runClient17`. No Offscreen binary is committed or published by HQA. The stock `:examples:runClient` profile remains the LWJGL 2 lane. The launcher does not download the Offscreen artifact. Use `gradlew.bat` on Windows and quote the complete `--gradle-argument` when its path contains spaces.

These scenarios verify event routing, independent GUI state, rendering and native resize. They do not automate OS focus changes, moving a window between monitors, mixed-DPI transitions or minimizing and restoring windows. Those require a real desktop integration check. Keep both windows rendering during unattended tests. The existing main-window key API preserves the supplied character after SDL routing. Printable characters also enter lwjgl3ify's public text-event channel, which its text fields require, without creating another legacy key press. The new Offscreen key API accepts only a character matching what Offscreen 0.3 derives from that native key and its modifiers. A mismatch fails before input is queued, rather than silently entering another character or duplicating Unicode input. Use matching native key/character pairs for additional windows. IME input is not covered by this API. Main-window resize dimensions are framebuffer pixels. Offscreen resize dimensions are logical SDL window units. An exact main-window pixel size that cannot be represented at the current pixel scale fails with a diagnostic.

Use `shiftClick` for interactions such as NEI's modifier-assisted recipe transfer. Shift press is dispatched through the real screen keyboard path and is visible through `Keyboard.isKeyDown` and `GuiScreen.isShiftKeyDown` during both mouse callbacks. The helper dispatches Shift release after the mouse release and clears held input even if a callback throws. It does not change NEI preferences or invoke recipe-transfer handlers directly.

Offscreen modifier clicks require the additional window to have native input focus. Horizon checks this precondition before sending Shift or a mouse press and reports a clear failure when focus is elsewhere. It does not silently perform an unmodified click or steal OS focus. Ordinary pointer clicks can still target an inactive Offscreen window.

Offscreen's adapter determines which GUI callbacks receive input. In 0.3, `VanillaUI` does not forward drag to `GuiScreen.mouseClickMove` or wheel input to `GuiScreen.handleMouseInput`. Polling-driven gestures can observe the held button during that window's update and rendering. The MUI2 adapter implements drag and scrolling callbacks. HQA sends the real SDL events and does not add callbacks missing from the production host.

The exact-ID example `horizonqaexamples:ClientWindowTests.offscreenDragKeepsInputScoped` checks held-button polling over multiple Offscreen frames, the release point, and absence of held input in the main window.

Offscreen 0.3 reduces wheel input consumed in one frame to a single signed notch. Use one `120` or `-120` scroll operation per awaited step when testing it. Native window integration does not change that host behavior.

## Sending a single key

Use `.key(Keyboard.KEY_SPACE, ' ')` for a Space shortcut or `.key(Keyboard.KEY_B, 'b')` to type a character in a scenario:

```java
scenario.key(Keyboard.KEY_SPACE, ' ')
    .click(searchField)
    .key(Keyboard.KEY_B, 'b')
    .client("search contains b", c -> assertSearchText(c, "b"));
```

The keycode is a nonzero LWJGL key constant. Pass `'\0'` for non-text keys. Horizon delivers the press with its character, then releases the key and clears held input even if a handler fails or closes the screen. An active GUI is required. The consumer selects focus through its own GUI interactions, such as clicking a text field, and separately asserts the result. This operation does not type whole strings or choose a focused widget. The scenario's `escape()` schedules the same dispatch path on the client thread. World interactions still use `useHeldItem()` and the configured use binding. `ClientKeyTests` exercises a Space shortcut, native text-field entry, Escape and handler-failure cleanup.

## Dragging from a live target

`drag(target).to(endpoint).overFrames(frames)` uses the same `ClientTarget` lookup and readiness as clicks for its start. The endpoint resolver returns a `java.awt.Point` in the active screen's coordinates and runs once immediately before the press. It can identify empty space and does not need a clickable widget. The positive `frames` argument controls the number of movement frames along a straight path. Each position is rendered with the button held before Horizon dispatches its motion event through normal screen input handling.

```java
ClientTarget handle = ClientTarget.of("toolbar handle", window -> findToolbarHandle(window));
scenario.drag(handle)
    .to(window -> new Point(window.screen().width / 2, window.screen().height / 2))
    .overFrames(6);
```

`findToolbarHandle` belongs to the consumer and returns a live `ClickTarget`. The drag defaults to the left button. Use `.button(index)` to select another LWJGL mouse button or `.to(x, y)` for a fixed GUI-coordinate endpoint. `overFrames` registers the gesture and returns the scenario. The next step waits for the drag to finish. Assert the resulting state in a separate step. Completion confirms dispatch and release, not acceptance by the GUI. A replacement screen, resize or handler failure aborts the drag and clears held input. Test teardown also releases the gesture. `ClientDragTests` contains runnable cases for movement across rendered frames, handler failure and screen replacement.

## Scrolling a live target

Use `scenario.scroll(panel, -120)` to scroll a live `ClientTarget` through the view's normal mouse handler. It uses the same live target lookup, rendered frame and hit-test readiness as semantic clicks. The delta is in raw signed LWJGL units, conventionally `120` upward or `-120` downward per notch. Horizon dispatches exactly one event with the supplied magnitude. Zero is rejected. The GUI decides what that event means, so wait for the resulting state in a separate sequence step. Completion confirms input dispatch and cleanup, not that the view moved. `ClientWheelTests` provides runnable moving-target and handler-failure cases.

## Using an item from the world

Use `.useHeldItem()` in a scenario to enter a GUI or perform a world interaction through normal input. A fixture may equip the player and prepare their position and aim. The operation requires an active world and player, a held item and no active screen. Missing prerequisites fail with a diagnostic instead of silently closing a screen or choosing an item.

```java
scenario.awaitClient("map equipped on client", c -> assertMapEquipped())
    .useHeldItem()
    .awaitClient("map visible", c -> assertMapScreen(c.screen()));
```

The assertions in this example belong to the consumer. `WorldInputTests` in `examples` provides runnable cases using a real written book equipped on the server and synchronized to the client.

Horizon reads the current use-item keybinding, which may be a mouse button or keyboard key. It supplies a press before a normal client tick and a release before the next tick. Minecraft processes those events through its normal input loop, including Forge input hooks. Horizon does not call the player controller, the item's use handler or a GUI factory to perform the action. Normal aiming still applies, so a block or entity under the crosshair may handle the interaction.

The future completes at client END after release processing. It proves that the input action finished, not that the item use was accepted. A mod may cancel input. Always assert the resulting state separately. These two input phases describe a press/release lifecycle, not a fixed wait for gameplay to finish.

Cleanup clears LWJGL state and Minecraft's cached keybinding state. A test failure invalidates pending work and releases input during client teardown before the next scenario. This action does not provide movement, arbitrary key gestures or continuous item use.

Core has no ModularUI, NEI, or consumer dependency. Resolve controls using the consumer's actual widget tree, visibility and clipping rules. Ambiguous controls must fail before clicking. The generic helper checks screen bounds but cannot infer widget identity or whether a library-specific rectangle is occluded.

## Targets that follow the live GUI

Define a reusable `ClientTarget` when an element can appear later or move during rendering. The resolver runs on each client tick until the click can be dispatched. It returns a `ClickTarget` from `com.gtnewhorizons.horizonqa.api.client`, or `null` while the element is absent, hidden or disabled. Throw an exception when the lookup is ambiguous or invalid. Exceptions from discovery and hit testing fail immediately with their original cause.

```java
ClientTarget saveTarget = ClientTarget.of("recipe-editor.save", window -> {
    if (!(window.screen() instanceof RecipeScreen)) return null;
    RecipeScreen screen = (RecipeScreen) window.screen();
    // These lookup and hit-test methods belong to the consumer's GUI integration
    Control save = screen.findUniqueControl("recipe-editor.save");
    if (save == null || !save.isVisible() || !save.isEnabled()) return null;
    return new ClickTarget(save, save.visibleBounds(), () -> screen.isActualMouseTarget(save));
});
scenario.click(saveTarget);
```

The description identifies the pending target in failure diagnostics. Discovery uses stable widget names or domain keys, scoped to the correct panel, instead of translated labels or list indices. For example, two panels may both contain a Save button. The resolver must distinguish them and reject multiple matches within its scope.

If a target ID comes from a fixture created during execution, compute that ID inside the resolver too. For example, use `ClientTarget.of("created asset action", c -> findAction(createdAssetId()))` rather than calling `createdAssetId()` while building the scenario. Java evaluates ordinary method arguments immediately, even when the called helper returns a deferred target. Wait for the fixture ID to be available and safely publish it before the client reads it. The target description can remain a stable label.

`ClickTarget(identity, visibleBounds, ready)` represents one current observation:

- `identity` is a live widget or a stable domain key compared with `Objects.equals`. Do not create a fresh identity object on every lookup.
- `visibleBounds` is a `java.awt.Rectangle` in the current `GuiScreen` coordinate system. Apply parent clipping before returning it. Horizon copies it and intersects it with the screen. Empty or off-screen areas keep waiting.
- `ready` checks the GUI's actual hit test for that element at the positioned pointer. It runs on the client thread after a normal rendered frame. Check overlap and cached hover state here. It must not invoke the element's action.

Horizon positions the pointer at the center of the visible area, awaits a normal completed frame, and resolves the target again before dispatch. A changed screen, screen dimensions, identity, or area that no longer contains the pointer causes repositioning and another frame. A continuously moving target can still be clicked if the current area contains the pointer and the real hit test accepts it. Resolvers and readiness checks observe GUI state and must not change the active screen or perform input.

The existing sequence budget bounds absence, clipping, motion and hit-test readiness together. Timeout diagnostics retain the target description and last wait reason. Horizon does not automatically scroll or open menus. Express those navigation actions explicitly before targeting an element outside the current view.

The runnable `ClientTargetTests` examples exercise a button that moves after pointer positioning, continuous motion and a resolver error that must not dispatch input. Existing point-based overloads remain available for fixed locations and existing integrations.

Frame capture reads the completed display framebuffer immediately before Minecraft's buffer swap. Pixel acquisition stays on the render thread. PNG encoding and file writes run asynchronously, and the future completes only after the file is written. Explicit checkpoints and the final diagnostic frame are retained under the test's artifact directory. Horizon does not compare screenshots or prove texture correctness merely from screen presence.

## Failure and cleanup

Existing JUnit XML and status JSON include the scenario, labeled step, tick budget, original exception stack, last screen and artifact paths. A client update/render exception remains the primary failure. If no completed frame exists after that exception, capture is skipped with a diagnostic instead of presenting a partial frame as valid evidence.

A request to open Minecraft's pause menu records the caller stack, previous screen, stored and reported pause state, display activity, logical game focus, focus-pause setting and GUI scale. These diagnostics are captured throughout the client-test run, including before a test attaches. They appear in the launch log and in the active case's output when a case exists. An intentional pause-menu test remains valid. An unexpected menu is diagnostic evidence and is not silently closed. `ClientPauseTests.serverInventorySynchronizesWhilePauseMenuRemainsOpen` verifies a real server inventory update while the menu stays open, followed by closing it through Escape input.

Ending a test invalidates queued client work immediately. An already claimed action may finish on the client thread. Client teardown then releases input, runs registered client cleanup and closes the screen. Server cleanup and the next test wait for teardown and final artifact writing. Teardown failure or its 100-tick deadline makes the run an infrastructure failure and prevents subsequent client scenarios from starting.

Cleanup retains a fatal error independently of earlier ordinary cleanup failures. Available restoration and result finalization run before the original fatal error is rethrown. Later cleanup callbacks still get their restoration opportunity.

The exact case `horizonqaexamples:ClientLifecycleTests.fatalCleanupRestoresThenPropagates` registers an ordinary cleanup error, a fatal linkage error and a final restoration callback. Run it alone in a separate client process and expect infrastructure exit `2`. Check `fatalCleanupRestorationCount=1` in the report and inspect the native crash output for the propagated fatal cause. Broad selections skip this destructive case.

The session remains active until final capture completes. A render failure after `succeed()` but before teardown finishes changes the result to an infrastructure error and retains the original cause. `ClientLateRenderFailureTests.renderFailureAfterSuccessPreservesCauseAndCleansOnce` exercises this boundary. The three exact `ClientCancellationTests` IDs exercise an external deadline after one passed case, verifying client cleanup, server cleanup and batch cleanup in order, with the final case remaining unstarted. These examples require exact selection because they intentionally terminate their client process with exit code `2`.

An integrated-server tick crash aborts the reported run with exit code `2`, retaining the original cause and the cases already observed. Unloading the test world also ends the client run as an infrastructure failure. These conditions stop further scenarios and close the test client without waiting for the launcher's external deadline. Ordinary assertion failures still follow the normal required/optional failure policy and allow the next test after successful cleanup.

`ClientLifecycleTests.clientLinkageFailureAbortsRun` and `ClientLifecycleTests.worldUnloadAbortsRun` exercise these terminal paths. Run each by its exact full test ID in a separate client process and expect exit code `2`. They skip in broader selections because deliberately terminating the world would prevent the remaining examples from running.

`GameTestHelper.afterTestAsync(maxTicks, supplier)` owns the single asynchronous teardown slot. The client session uses it, so consumers register client cleanup through the scenario's `afterTest(c -> ...)` or the low-level `ClientTest.afterTest` inside a client action. Registration is deferred until the scenario cleanup-registration step executes. If setup captures state for restoration, register its cleanup inside that same client setup callback. Ordinary `helper.afterTest` callbacks continue to run on the server thread after client teardown.

After the reported run ends, final client teardown has a separate five-second wall-clock budget. Expiry records `CLIENT_SHUTDOWN`, performs available client restoration and ends the pending teardown without awaiting more frames or screenshot encoding. The result remains an infrastructure error. Tick budgets cannot detect a frozen game loop. Use the bounded launcher for unattended runs and retain `launch-result.json`, `launch.log`, and any thread dump alongside the normal reports.

Client startup failures include the current screen, world/player/server availability and JVM thread stacks in the status JSON and JUnit XML. The parallel CI scenario prepares the client runtime with `:examples:prepareClientTests` before starting its overlap deadline, so cold dependency preparation does not consume the time reserved for booting and exercising two clients.
