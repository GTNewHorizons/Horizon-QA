package com.gtnewhorizons.horizonqa.examples.tests;

import static org.lwjgl.sdl.SDLEvents.*;
import static org.lwjgl.sdl.SDLKeycode.*;
import static org.lwjgl.sdl.SDLScancode.*;

import java.nio.ByteBuffer;

import net.minecraft.client.gui.GuiScreen;

import org.lwjgl.sdl.SDLKeyboard;
import org.lwjgl.sdl.SDL_Event;

import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.gtnewhorizons.horizonqa.api.client.ClientTest;
import com.gtnewhorizons.horizonqa.client.SdlInput;

import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;

/** Supplies native SDL input while observing the game's event and keyboard polling paths */
@Lwjgl3Aware
@GameTestHolder(value = "horizonqaexamples", clientOnly = true, requiredMods = "lwjgl3ify")
public final class ClientSdlIsolationTests {

    private ClientSdlIsolationTests() {}

    @GameTest(template = "client_smoke", timeoutTicks = 300)
    public static void nativeKeyboardCannotAffectAutomatedInput(GameTestHelper helper) {
        ClientTest.scenario(helper)
            .client("native SDL keyboard is isolated", client -> {
                try (SDL_Event event = SDL_Event.calloc()) {
                    event.key()
                        .type(SDL_EVENT_KEY_DOWN)
                        .windowID(SdlInput.mainWindowId())
                        .scancode(SDL_SCANCODE_LSHIFT)
                        .key(SDLK_LSHIFT)
                        .down(true);
                    helper.assertTrue(!SDL_PushEvent(event), "Physical key events must be discarded");
                }
                try (SDL_Event event = SDL_Event.calloc()) {
                    event.text()
                        .type(SDL_EVENT_TEXT_INPUT)
                        .windowID(SdlInput.mainWindowId());
                    helper.assertTrue(!SDL_PushEvent(event), "Physical text events must be discarded");
                }
                ByteBuffer nativeKeys = SDLKeyboard.SDL_GetKeyboardState();
                byte previous = nativeKeys.get(SDL_SCANCODE_LSHIFT);
                nativeKeys.put(SDL_SCANCODE_LSHIFT, (byte) 1);
                try {
                    helper.assertTrue(!GuiScreen.isShiftKeyDown(), "Native polled Shift must not reach the game");
                } finally {
                    nativeKeys.put(SDL_SCANCODE_LSHIFT, previous);
                }
            })
            .succeed();
    }
}
