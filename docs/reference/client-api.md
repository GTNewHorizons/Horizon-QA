---
title: Client API
description: Client scenario methods, live targets, thread ownership, timeouts, and cleanup contracts.
---

# Client API

Start with the [client test guide](../guide/client-tests.md) for a runnable example.
`ClientTest`, `ClientScenario`, `ClientTarget`, and `ClickTarget` are in
`com.gtnewhorizons.horizonqa.api.client`; keep them in `clientOnly = true` holders.
See [Javadoc](https://www.gtnewhorizons.com/Horizon-QA/javadoc/com/gtnewhorizons/horizonqa/api/client/ClientScenario.html)
for every overload.

## Fluent contract

Build `ClientTest.scenario(helper)` on the server test thread and finish with `succeed()`.
It attaches one client session to the existing sequence; registrations defer work until execution.
Do not also call `helper.startSequence()`.

| Operation | Behavior |
|---|---|
| `client(label, callback)` | Run once at client END |
| `server(label, callback)` | Run once at server END; must return promptly |
| `awaitClient(label, assertion)` | Retry client assertions, at most one attempt in flight |
| `awaitServer(label, assertion)` | Poll authoritative server assertions at END after the world tick |
| `awaitScreen(type)` | Wait for the live screen type on the client thread |
| `click(target)`, `rightClick(target)` | Resolve a live target, send one native click, observe release |
| `doubleClick(target)` | Resolve once; send two native left clicks at the same position; observe processing and release |
| `shiftClick(target)` | Own Shift around one left-click; observe mouse and modifier release |
| `scroll(target, notches)` | Send native wheel input at a live target; positive scrolls down, negative up |
| `drag(target, endpoint, movementFrames)` | Left-button drag to a client-resolved scaled GUI point; observe release |
| `resizeWindow(width, height)` | Resize the native window; await Minecraft's normal resize/rendering |
| `useHeldItem()` | Send the configured use-item binding with no GUI |
| `key(KeyEvent.VK_*)`, `escape()` | Send one physical key press/release; no text-entry guarantee |
| `capture(name)` | Capture a completed framebuffer; encode PNG off the game thread |
| `async(label, c -> stage)` | Start once on the server thread; await completion without blocking |
| `serverSequence()` | Expose the same sequence for advanced START/END registrations |

Assertion waits retry only completed assertion failures; other exceptions fail immediately with
their original cause. Observation callbacks must not mutate state. Actions are never retried
because a later assertion fails. Await the resulting GUI or server state separately from input release.
Screenshots are diagnostics, not pixel correctness assertions.

Clicks queue native press/release together, then await ordinary input processing and release.
They reject an already-held button. Use `drag` for a held-button gesture.

Use `doubleClick` when two presses must reach the same position before camera or layout changes.
It sends both press/release pairs without intervening game frames and rejects an already-held
left button. Two separate `click` steps each resolve rendered readiness and may follow a moving target.

Wheel counts must be nonzero and between -100 and 100. Drag movement frames describe the gesture's
trajectory; they do not replace readiness assertions. A drag rejects an already-held left button,
an endpoint outside the screen, or a screen/size change while pressed. Both gestures resolve targets
on the client thread and retain the session's input release and bounded cleanup.

Client state belongs inside `client` / `awaitClient` callbacks. An `async` supplier runs on the
**server** thread: use queued session methods such as `c.run(...)` or `c.click(...)` there.
Never block either game thread with `get()` / `join()`. START runs before the world tick;
END callbacks, polling, and timeout evaluation run after it.

## Live click targets

`ClientTarget.of("name", resolver)` names a reusable deferred lookup; creating it reads no client
state. Its resolver runs on the client thread and returns a `ClickTarget` containing:

- **Identity:** the live widget object, compared by reference during pointer positioning.
- **Bounds:** a `java.awt.Rectangle` in current scaled GUI pixels.
- **Readiness:** a predicate for the widget's actual hit-test readiness after rendering at the pointer.

Resolve current screen, bounds, and readiness each time. Return `null` while absent; throw on
ambiguity. GUI toolkit lookup belongs to the consumer. HQA clips bounds, positions the native pointer,
awaits rendered readiness, then presses/releases through normal Minecraft/Forge input.

Shift-click rejects an already-held Shift key and releases its modifier on gesture errors.
Timeout/cancellation invalidates queued work and enters session teardown. See
`ClientInteractionTests.aNativeButton` in the [examples](../contributing/examples-mod.md) for target
resolution and gesture recovery.

## Budgets

| Limit | Budget |
|---|---|
| First rendered integrated world/player | 600 wall-clock seconds; test ticks have not started |
| Whole test | `@GameTest(timeoutTicks = ...)` server END ticks |
| Bounded scenario step | 100 server END ticks by default, including time in flight |
| Client teardown | 100 server END ticks |
| Final client shutdown | Five wall-clock seconds |
| Whole JVM launch | External launcher's deadline |

`defaultTimeoutTicks(40)` changes subsequently registered steps. Per-operation overloads such as
`click(target, 60)`, `awaitScreen(type, 80)`, or `awaitClient("reply", 80, assertion)` affect only
that step. Tick budgets must be positive; synchronous `server` callbacks have no step budget.

## Cleanup and custom operations

Register restoration before mutation, on the owning thread. For example, inside a scenario:

```java
.client("temporary GUI scale", c -> {
    Minecraft mc = Minecraft.getMinecraft();
    int original = mc.gameSettings.guiScale;
    c.afterTest(() -> mc.gameSettings.guiScale = original);
    mc.gameSettings.guiScale = 2;
})
```

Register server restoration with `helper.afterTest`. Client teardown cancels pending work, releases
owned input through the game loop, captures the final frame, and restores registered client state,
the original screen, and an ungrabbed GUI pointer before server cleanup. Failed/incomplete cleanup
aborts subsequent cases. Disconnect, world loss, cancellation, and client/server loop crashes are
infrastructure failures even for optional cases; restoration is best effort after fatal JVM failure.

For lower-level authoring, `ClientTest.attach(helper)` works with the existing sequence's
`thenExecuteAsync` / `thenWaitUntilAsync`. `ClientTest.run` queues client END work; `screen`,
`afterTest`, `holdKey`, and `releaseKey` require that client callback. `useItem()` is the queued
counterpart of fluent `useHeldItem()`. Both styles share input, cleanup, and reporting.

`requestWindowResize(width, height)` requests resizing inside a client callback without awaiting
completion; observe framebuffer dimensions separately. `resizeWindow` awaits them for you.
Both restore the original native window and framebuffer size before the next test; a created,
resizable, windowed display without an AWT parent is required.

Client-test mode keeps the integrated world ticking with GUIs open and disables pause on focus loss;
it does not test normal pause semantics. HeadlessNH 1.1.1 dismisses unsolicited pause menus, so
pause-menu input tests need a launcher without that override. Shader/device-specific rendering tests
must provide their own environment and correctness assertions.
