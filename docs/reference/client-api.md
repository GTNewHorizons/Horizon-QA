---
title: Client API
description: Look up scenario, live-target, window and low-level client contracts.
---

# Client API

Client types live in `com.gtnewhorizons.horizonqa.api.client`. Use the [client test guide](../guide/client-tests.md) for examples and [portable tools](../guide/client-tools.md) for launch and verification. Javadoc contains every overload.

## Sessions and target observations

| Entry point | Contract |
| --- | --- |
| `ClientTest.scenario(helper)` | Create one fluent scenario backed by one existing sequence and one client session |
| `ClientTest.attach(helper)` | Attach a session for direct `GameTestSequence` authoring |
| `ClientTarget.of(description, resolver)` | Store a stable description and lazy `Function<ClientWindow, ClickTarget>` observation |
| `ClickTarget(identity, visibleBounds, ready)` | Describe a live widget/domain identity, its clipped GUI rectangle and actual hit-test readiness |
| `ClientTest.mainWindow()` | Access the session's main window |
| `ClientTest.offscreenWindow(windowId)` | Bind an existing optional Offscreen window by SDL ID on the client thread |

Attach at most one session per test. A scenario already attaches it and creates the sequence. The session owns the single asynchronous teardown slot. Register client restoration through the scenario or inside a client setup callback. Ordinary `helper.afterTest` callbacks run on the server thread after client teardown.

A `ClientTarget` resolver receives the window currently being observed and runs on the client thread. Return `null` while unavailable and throw for ambiguous or invalid lookup. Resolvers and readiness predicates only observe state. The same target works across main and additional windows. See [live-target rules](../guide/client-tests.md#targets-that-follow-the-live-gui).

## Scenario methods

| Scenario method | Contract |
|---|---|
| `useHeldItem()` | Use the equipped item through its configured world-input binding |
| `awaitScreen(type)` | Wait until the active screen has the requested type |
| `click(target)`, `rightClick(target)`, `shiftClick(target)` | Resolve a live target and dispatch a left, right or Shift-left click |
| `scroll(target, delta)` | Dispatch one signed native wheel event at a live target |
| `drag(target).to(endpoint).overFrames(n)` | Hold the mouse through rendered movement frames and release at the endpoint |
| `key(code, character)`, `escape()` | Dispatch native key input through the active screen |
| `capture(checkpoint)` | Wait for a rendered PNG checkpoint to be written |
| `resizeWindow(width, height)` | Resize the existing OS window, await normal game resize handling and a rendered frame |
| `client(label, action)`, `server(label, action)` | Run a custom action once on the indicated thread |
| `awaitClient(label, assertion)`, `awaitServer(label, assertion)` | Retry assertions on the indicated thread |
| `awaitServerAccelerated(label, multiplier, assertion)` | Retry server assertions while requesting accelerated full server ticks |
| `async(label, action)`, `awaitAsync(label, assertion)` | Await custom asynchronous work once or retry completed assertion failures |
| `afterTest(cleanup)` | Register client cleanup when this step executes |
| `serverSequence()` | Access this scenario's existing sequence for advanced scheduling |
| `succeed()` | Append the success step and finish authoring |

| Step option | Contract |
| --- | --- |
| `step(label)` | Override the next step description |
| `withinTicks(n)` | Override the next bounded step budget |
| `defaultTimeoutTicks(n)` | Change subsequent bounded-step defaults, initially 100 ticks |
| `drag(target).button(index)` | Select the mouse button for the pending drag |

Scenario target gestures operate on the session's main window. `client` actions run at client END, and `server` actions run at server END. `async` and `awaitAsync` callbacks begin on the server thread and compose queued client futures. Do not read Minecraft client state directly there or block either game thread. See [budget rules](../guide/client-tests.md#budgets-and-step-descriptions).

## Window operations

| Window operation | Contract |
| --- | --- |
| `run(w -> ...)` | Observe this window's screen inside its GUI context on the client thread |
| `screen()` / `screen(ScreenType.class)` | Read or assert the bound window's screen inside a client operation |
| `click`, `shiftClick`, `scroll`, `drag`, `key` | Target this window through its input backend, using its GUI coordinate system |
| `click(button, target)`, `shiftClick(button, target)` | Resolve a named `ClientTarget` against this window before the click |
| `scroll(delta, target)` | Send one signed wheel event at a named target in this window |
| `drag(button, target, endpoint, frames)` | Resolve a named start target and a `Function<ClientWindow, Point>` endpoint in this window |
| `resize(width, height)` | Request native resize and await the bound window's own completed frame |
| `capture(checkpoint)` | Save a completed frame of this window and await the PNG write |
| `close()` | Close a test-owned additional window. Closing the main game window is rejected |
| `isOpen()` | Observe whether the original bound window still exists |

Named-target click, Shift-click, scroll and drag conveniences accept a `ClientTarget` and resolve it against the bound `ClientWindow`. Queue an assertion with that window after input to verify its effect. The [multiple-window guide](../guide/client-tests.md#multiple-windows-and-optional-offscreen-support) defines ownership, prerequisites, pixel units and host limitations.

## Low-level main-window operations

These `ClientTest` methods remain available for direct sequence authoring and specialized future composition. Point callbacks and raw resolvers here receive `ClientTest` and use the main window. Named `ClientTarget` objects use the window methods above, with `ClientWindow` as their resolver context.

| Client method | Contract |
|---|---|
| `screen()` | Real current screen, possibly null, inside a client action |
| `screen(Class<T>)` | Assert and return its type, with the observed class in failure output |
| `useHeldItem()` | Press the configured use-item control through normal world input, release it on the next client tick, and complete after processing |
| `click(button, target)` | Resolve a `java.awt.Point` on the client thread, position the virtual pointer, await a normal frame, then dispatch press/release through `GuiScreen.handleInput` |
| `click(button, target, ready)` | Also await a consumer `Predicate<ClientTest>` before dispatch, for cached hover/hit-test state |
| `shiftClick(button, target, ready)` | The same awaited click while Shift is held through mouse press and release |
| `click(button, description, resolver)` | Repeatedly resolve a live `ClickTarget`, wait for visible bounds and actual hit-test readiness, then dispatch a real click |
| `shiftClick(button, description, resolver)` | The same dynamically resolved click with Shift held through mouse release |
| `scroll(wheelDelta, description, resolver)` | Resolve a live target and dispatch one mouse-wheel event at its current position |
| `drag(button, description, start, end, frames)` | Resolve a live start target, hold the button through rendered movement frames, then release at a plain screen-coordinate endpoint |
| `escape()` | Escape press and release through the current screen's keyboard dispatch |
| `key(keyCode, character)` | Queue one LWJGL key press with a typed character, followed by release through the active screen |
| `afterTest(Runnable)` | Register client cleanup inside a client action |
| `capture(checkpoint)` | Await a rendered PNG and obtain its `File` path |

`run(Consumer<ClientTest>)` queues work at client END and returns `CompletableFuture<Void>`. A semantic target repeatedly resolves its current rectangle and hit test. A raw point overload uses the caller's location and can take an explicit readiness predicate. Neither form proves the GUI accepted the action. Assert resulting client and server state separately.

Scenario and low-level operations share one queue, one sequence and one cleanup lifecycle.
