package com.gtnewhorizons.horizonqa.client;

import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import org.lwjgl.opengl.Display;

import com.gtnewhorizons.horizonqa.api.client.ClientTest;

/** Uses the existing main-window runtime while sharing gesture validation with additional windows */
public final class MainWindowDriver implements WindowDriver {

    private final LongSupplier frames;

    public MainWindowDriver(LongSupplier frames) {
        this.frames = frames;
    }

    public String description() {
        return "main window";
    }

    public boolean isOpen() {
        return Display.isCreated();
    }

    public GuiScreen screen() {
        return Minecraft.getMinecraft().currentScreen;
    }

    public long renderedFrames() {
        return frames.getAsLong();
    }

    public <T> T observe(Supplier<T> observation) {
        return observation.get();
    }

    public void move(GuiScreen screen, int x, int y) {
        LwjglInput.move(screen, x, y);
    }

    public void mouse(GuiScreen screen, int x, int y, int button, boolean down) {
        LwjglInput.mouse(screen, x, y, button, down);
    }

    public void scroll(GuiScreen screen, int x, int y, int delta) {
        LwjglInput.scroll(screen, x, y, delta);
    }

    public void key(GuiScreen screen, int key, char character, boolean down) {
        LwjglInput.key(screen, key, character, down);
    }

    public void shift(GuiScreen screen, boolean down) {
        LwjglInput.shift(screen, down);
    }

    public void beginDrag(GuiScreen screen, int x, int y, int button) {
        LwjglInput.beginDrag(screen, x, y, button);
    }

    public void dragMove(GuiScreen screen, int x, int y) {
        LwjglInput.dragMove(screen, x, y);
    }

    public boolean isDragging() {
        return LwjglInput.isDragging();
    }

    public CompletableFuture<Void> inputConsumed() {
        return CompletableFuture.completedFuture(null);
    }

    public void release() {
        LwjglInput.release();
    }

    public CompletableFuture<Void> resize(ClientTest owner, int width, int height) {
        return owner.resizeWindow(width, height);
    }

    public CompletableFuture<File> capture(ClientTest owner, String checkpoint) {
        return owner.capture(checkpoint);
    }

    public void close() {
        throw new IllegalStateException("The main game window is owned by the client runtime");
    }
}
