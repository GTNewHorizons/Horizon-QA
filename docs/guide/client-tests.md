---
title: Client tests
description: Watch the examples, write a client test, and diagnose its results.
---

# Client tests

Use client tests for GUI input, client state, rendering, and the gameplay caused by those actions.
They run inside a Minecraft client in **singleplayer**: Minecraft starts the integrated server;
HQA coordinates its server thread and the client thread. Testing a client connected to a dedicated
server is currently unsupported.

## Visible local runs

From this repository's root, launch the examples with the Java 8 client task:

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

1. Create a **Creative / Superflat** singleplayer world within 600 seconds.
2. Keep the window focused and stop using the mouse and keyboard while tests run.
3. Watch the button and chest interactions. HQA writes reports to `$demo_dir` and closes the game.

This uses your desktop display and Linux's `timeout`; Python, Xvfb, and xdotool are unnecessary.
Ordinary example launches omit HeadlessNH so you control world creation.

## Write a test

[Add HQA to your mod](../getting-started/mod-setup.md), then put client tests in a separate
`clientOnly = true` holder. This keeps client classes out of dedicated-server discovery.
The following test presses **T**, waits for chat to open, captures it, then closes it with Escape:

```java
import java.awt.event.KeyEvent;

import net.minecraft.client.gui.GuiChat;

import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.gtnewhorizons.horizonqa.api.client.ClientTest;

@GameTestHolder(value = "mymod", clientOnly = true)
public final class ChatTests {
    @GameTest(timeoutTicks = 200)
    public static void openAndCloseChat(GameTestHelper helper) {
        ClientTest.scenario(helper)
            .key(KeyEvent.VK_T)
            .awaitScreen(GuiChat.class)
            .capture("chat")
            .escape()
            .awaitClient("chat closed", c -> {
                if (c.screen() != null) throw new AssertionError("Expected chat to close");
            })
            .succeed();
    }
}
```

This assumes the default **T** chat binding. Its test ID is `mymod:ChatTests.openAndCloseChat`.
The key presses go through the operating system and Minecraft's normal input handling.

The essential rules are:

- Use `client` / `awaitClient` to access client state, and `server` / `awaitServer` for authoritative
  world or inventory state. Test methods, fixtures, and batch hooks run on the server thread.
- Actions run once. Assertion waits retry until they pass or time out; wait for the result of an
  action rather than sleeping fixed ticks. A click completing means input was released.
- Fixture setup may equip or position a player. The behavior under test must use real input and
  Minecraft/Forge handlers. Wait for fixture packets to reach the client before interacting.
- Register restoration **before** changing state: `c.afterTest` for client state,
  `helper.afterTest` for server state. HQA releases its input and completes client cleanup before
  server cleanup and the next test. Cleanup failure aborts the remaining tests.
- Finish the chain with `succeed()`. It owns the test's sequence; do not also start another one or
  block a game thread with `get()` / `join()`.

See the [client API reference](../reference/client-api.md) for gestures, live click targets, budgets,
and custom operations. The runnable
[`ClientInteractionTests`](../contributing/examples-mod.md#what-each-test-class-covers) includes
authoritative chest assertions and checks that successive tests leave no state behind.

## Automated development runs

On Linux, install Python 3.9+, Xvfb, xdotool, and an AWT-capable Java runtime, then run:

```bash
./gradlew hqaClientTest
```

This runs the same examples on a private display, creates a world through HeadlessNH, and verifies
fresh JSON/JUnit reports. Each run gets a new directory under `build/horizonqa/`.
Select one case with `-PhqaTests=horizonqaexamples:ClientInteractionTests.bChestGameplay`.

For your mod's Gradle task, packaged games, or an existing CI launcher, follow
[CI tooling](ci-tools.md). Install HQA and the test JARs in the client; packaged tests need no
source checkout or Gradle wrapper.

## Speed up simulation waits

Use `awaitServerAccelerated("construction completed", 600, 10, assertion)` for server simulation
that can run independently of client input and wall-clock timing. It runs up to ten complete server
ticks per normal tick slot only while that assertion is waiting, then restores normal speed.
Client input and synchronization waits stay at normal speed. See the
[accelerated-wait contract](../reference/client-api.md#accelerated-server-waits).

Run the same real furnace at normal speed and 10× speed, followed by a cleanup check:

```bash
./gradlew hqaClientTest -PhqaTests=horizonqaexamples:ClientAccelerationTests
```

Each case records simulation ticks and wall-clock seconds in its existing report diagnostics.

## Diagnose a failure

Read `horizonqa-result.json` and the failing JUnit case's `system-out` first. Diagnostics include
`lastScreen`, `waitingFor`, and screenshot paths under `artifacts/`. Automated launches also retain
`launch.log` and `launch-result.json`; use those and the JVM crash report if startup produced no HQA reports.

| Exit | Meaning |
|---|---|
| `0` | Passed; optional failures/timeouts are JUnit-skipped |
| `1` | Required assertion failure or timeout |
| `2` | Infrastructure error, cancellation, cleanup/reporting failure, or incomplete execution |

| Problem | Check |
|---|---|
| `CLIENT_CONFIG` | Required [client-mode properties](../reference/jvm-flags.md#horizonqaclient) |
| `CLIENT_STARTUP` | A singleplayer player/world must render with menus closed within 600 seconds |
| Input failure | Actual window focus and `java.awt.headless=false`; automation also needs Xvfb/xdotool |
| Step timeout | Named step, screen type, live target readiness, and synchronized fixture/player state |
| HeadlessNH marker error | With HeadlessNH 1.1.1, omit it for manual launches; its disabled mode repeats marker writes |
| Missing/inconsistent reports | Launcher logs and [report verification](ci-tools.md#verify-reports); never treat these as success |

Reproduce the exact test ID in a fresh run directory. Increase a timeout only when the readiness
condition is correct and the environment needs more time. `ClientFailureTests` contains intentional
failures; select its individual cases rather than using it as a passing smoke suite.
