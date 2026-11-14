package com.gtnewhorizons.horizonqa.examples.tests;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SoundCategory;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.InputImplementation;

import com.gtnewhorizons.horizonqa.HorizonQAMod;
import com.gtnewhorizons.horizonqa.HorizonQAProperties;
import com.gtnewhorizons.horizonqa.api.ManualSession;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.gtnewhorizons.horizonqa.client.LwjglInput;
import com.gtnewhorizons.horizonqa.internal.GameTestRunner;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Finite real-client verification of the lab path, separate from the interactive example. */
@GameTestHolder(value = "horizonqaexamples", clientOnly = true)
public final class ManualLaboratoryTests {

    private ManualLaboratoryTests() {}

    @ManualSession.Setup
    public static void verifiesControlsAndCleanup(ManualSession session) {
        AtomicBoolean cleaned = new AtomicBoolean();
        session.onClose(() -> cleaned.set(true));
        ManualLaboratoryExample.prepare(session);
        NativeControls controls = new NativeControls();
        session.onClose(
            () -> FMLCommonHandler.instance()
                .bus()
                .unregister(controls));
        FMLCommonHandler.instance()
            .bus()
            .register(controls);
        session.onTick(() -> {
            if (!controls.checked.isDone()) return;
            controls.checked.join();
            session.close();
            if (!cleaned.get() || GameTestRunner.isManualActive()) {
                throw new AssertionError("Manual closure did not restore state and release execution");
            }
            HorizonQAMod.LOG.info("MANUAL_LAB_VERIFIED controls, audio, world ticks and cleanup");
            Minecraft.getMinecraft()
                .func_152344_a(() -> {
                    Minecraft mc = Minecraft.getMinecraft();
                    mc.loadWorld(null);
                    mc.shutdown();
                    FMLCommonHandler.instance()
                        .exitJava(0, false);
                });
        });
    }

    public static final class NativeControls {

        private final CompletableFuture<Void> checked = new CompletableFuture<>();
        private Object mouse;
        private Object keyboard;
        private int observations;

        @SubscribeEvent
        public void tick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END || checked.isDone()) return;
            try {
                if (LwjglInput.usesSdl()) throw new AssertionError("This native supply fixture requires LWJGL2");
                if (HorizonQAProperties.clientAutomationEnabled()) throw new AssertionError("Lab enabled automation");
                if (mouse == null) {
                    LwjglInput.restoreInput();
                    mouse = FieldUtils.readDeclaredStaticField(Mouse.class, "implementation", true);
                    keyboard = FieldUtils.readDeclaredStaticField(Keyboard.class, "implementation", true);
                    FieldUtils.writeDeclaredStaticField(Mouse.class, "implementation", supply(mouse), true);
                    FieldUtils.writeDeclaredStaticField(Keyboard.class, "implementation", supply(keyboard), true);
                    return;
                }
                Mouse.poll();
                Keyboard.poll();
                if (!Mouse.isButtonDown(0) || !Keyboard.isKeyDown(Keyboard.KEY_Q)) {
                    throw new AssertionError(
                        "Lab discarded native mouse or keyboard polling after another client tick");
                }
                Minecraft mc = Minecraft.getMinecraft();
                for (SoundCategory category : new SoundCategory[] { SoundCategory.MUSIC, SoundCategory.WEATHER,
                    SoundCategory.MOBS, SoundCategory.ANIMALS }) {
                    if (mc.gameSettings.getSoundLevel(category) != 0.0F) {
                        throw new AssertionError("Lab did not silence " + category);
                    }
                }
                if (mc.gameSettings.getSoundLevel(SoundCategory.MASTER) != 0.5F
                    || mc.gameSettings.getSoundLevel(SoundCategory.BLOCKS) != 0.5F) {
                    throw new AssertionError("Lab master and block volume must be 50%");
                }
                if (++observations == 3) {
                    restore();
                    checked.complete(null);
                }
            } catch (Throwable failure) {
                try {
                    restore();
                } catch (ReflectiveOperationException cleanup) {
                    failure.addSuppressed(cleanup);
                }
                checked.completeExceptionally(failure);
            }
        }

        private Object supply(Object original) {
            return Proxy.newProxyInstance(
                InputImplementation.class.getClassLoader(),
                new Class<?>[] { InputImplementation.class },
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "pollMouse":
                            ((IntBuffer) args[0]).put(0, 17)
                                .put(1, 23)
                                .put(2, 0);
                            ((ByteBuffer) args[1]).put(0, (byte) 1);
                            return null;
                        case "pollKeyboard":
                            ByteBuffer keys = (ByteBuffer) args[0];
                            for (int i = 0; i < keys.capacity(); i++) keys.put(i, (byte) 0);
                            keys.put(Keyboard.KEY_Q, (byte) 1);
                            return null;
                        case "readMouse":
                        case "readKeyboard":
                            return null;
                        default:
                            try {
                                return method.invoke(original, args);
                            } catch (InvocationTargetException failure) {
                                throw failure.getCause();
                            }
                    }
                });
        }

        private void restore() throws IllegalAccessException {
            LwjglInput.restoreInput();
            if (mouse != null) FieldUtils.writeDeclaredStaticField(Mouse.class, "implementation", mouse, true);
            if (keyboard != null) FieldUtils.writeDeclaredStaticField(Keyboard.class, "implementation", keyboard, true);
            Mouse.poll();
            Keyboard.poll();
        }
    }
}
