package com.gtnewhorizons.horizonqa.client;

import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import net.minecraft.client.gui.GuiScreen;

import com.gtnewhorizons.horizonqa.api.client.ClientTest;

/** Runtime-specific window operations behind the shared client gesture lifecycle */
public interface WindowDriver {

    String description();

    boolean isOpen();

    GuiScreen screen();

    long renderedFrames();

    <T> T observe(Supplier<T> observation);

    void move(GuiScreen screen, int x, int y);

    void mouse(GuiScreen screen, int x, int y, int button, boolean down);

    void scroll(GuiScreen screen, int x, int y, int delta);

    void key(GuiScreen screen, int key, char character, boolean down);

    void shift(GuiScreen screen, boolean down);

    void beginDrag(GuiScreen screen, int x, int y, int button);

    void dragMove(GuiScreen screen, int x, int y);

    boolean isDragging();

    void release();

    CompletableFuture<Void> inputConsumed();

    CompletableFuture<Void> resize(ClientTest owner, int width, int height);

    CompletableFuture<File> capture(ClientTest owner, String checkpoint);

    void close();
}
