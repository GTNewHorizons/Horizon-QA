package com.gtnewhorizons.horizonqa.examples.tests;

import java.awt.Rectangle;
import java.util.concurrent.CompletionException;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

import org.lwjgl.input.Keyboard;

import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.gtnewhorizons.horizonqa.api.client.ClickTarget;
import com.gtnewhorizons.horizonqa.api.client.ClientTarget;
import com.gtnewhorizons.horizonqa.api.client.ClientTest;

/** Keyboard events exercise a real focused text field and the screen's ordinary shortcut handling. */
@GameTestHolder(value = "horizonqaexamples", clientOnly = true)
public final class ClientKeyTests {

    private ClientKeyTests() {}

    @GameTest(template = "client_smoke", timeoutTicks = 300)
    public static void sendsShortcutAndCharacterThroughFocusedScreen(GameTestHelper helper) {
        ClientTest.scenario(helper)
            .defaultTimeoutTicks(40)
            .client(
                "open keyboard screen",
                c -> Minecraft.getMinecraft()
                    .displayGuiScreen(new KeyScreen()))
            .key(Keyboard.KEY_SPACE, ' ')
            .click(
                ClientTarget.of(
                    "text field",
                    c -> c.screen(KeyScreen.class)
                        .target()))
            .key(Keyboard.KEY_B, 'b')
            .client("field received character and shortcut fired once", c -> {
                KeyScreen screen = c.screen(KeyScreen.class);
                if (!"b".equals(screen.field.getText()) || screen.spaces != 1
                    || screen.presses != 2
                    || screen.releases != 2)
                    throw new AssertionError("Keycode, character or press/release dispatch was lost");
                assertReleased(Keyboard.KEY_B);
                assertReleased(Keyboard.KEY_SPACE);
            })
            .key(Keyboard.KEY_ESCAPE, '\0')
            .client("Escape released after screen close", c -> {
                if (c.screen() != null) throw new AssertionError("Escape did not close screen");
                assertReleased(Keyboard.KEY_ESCAPE);
            })
            .succeed();
    }

    @GameTest(template = "client_smoke", timeoutTicks = 300)
    public static void keyHandlerFailureReleasesInputAndAllowsNextKey(GameTestHelper helper) {
        ClientTest.scenario(helper)
            .defaultTimeoutTicks(40)
            .client("open throwing keyboard screen", c -> {
                KeyScreen screen = new KeyScreen();
                screen.fail = true;
                Minecraft.getMinecraft()
                    .displayGuiScreen(screen);
            })
            .async(
                "original key failure is preserved",
                client -> client.key(Keyboard.KEY_B, 'b')
                    .handle((ignored, error) -> {
                        Throwable cause = error;
                        while (cause instanceof CompletionException) cause = cause.getCause();
                        if (!(cause instanceof IllegalStateException)
                            || !"Intentional key handler failure".equals(cause.getMessage()))
                            throw new AssertionError("Original keyboard failure was not preserved", error);
                        return null;
                    }))
            .client("failed key left no held input", c -> {
                assertReleased(Keyboard.KEY_B);
                c.screen(KeyScreen.class).fail = false;
            })
            .key(Keyboard.KEY_SPACE, ' ')
            .client("next shortcut arrived and released", c -> {
                if (c.screen(KeyScreen.class).spaces != 1) throw new AssertionError("Keyboard did not recover");
                assertReleased(Keyboard.KEY_SPACE);
            })
            .succeed();
    }

    private static void assertReleased(int key) {
        if (Keyboard.isKeyDown(key)) throw new AssertionError("Keyboard key remained held");
    }

    private static final class KeyScreen extends GuiScreen {

        private final Rectangle area = new Rectangle(30, 30, 140, 20);
        private GuiTextField field;
        private int renderedX;
        private int renderedY;
        private int spaces;
        private int presses;
        private int releases;
        private boolean fail;

        @Override
        public void initGui() {
            field = new GuiTextField(fontRendererObj, area.x, area.y, area.width, area.height);
        }

        private ClickTarget target() {
            return new ClickTarget(field, area, () -> area.contains(renderedX, renderedY));
        }

        @Override
        public void drawScreen(int x, int y, float partialTicks) {
            renderedX = x;
            renderedY = y;
            field.drawTextBox();
            super.drawScreen(x, y, partialTicks);
        }

        @Override
        protected void mouseClicked(int x, int y, int button) {
            field.mouseClicked(x, y, button);
            super.mouseClicked(x, y, button);
        }

        @Override
        public void handleKeyboardInput() {
            int key = Keyboard.getEventKey();
            if (Keyboard.getEventKeyState()) {
                if (!Keyboard.isKeyDown(key)) throw new AssertionError("Polled key state did not precede press");
                presses++;
                if (fail) throw new IllegalStateException("Intentional key handler failure");
            } else {
                assertReleased(key);
                releases++;
            }
            super.handleKeyboardInput();
        }

        @Override
        protected void keyTyped(char character, int key) {
            if (field.textboxKeyTyped(character, key)) return;
            if (key == Keyboard.KEY_SPACE) spaces++;
            else super.keyTyped(character, key);
        }
    }
}
