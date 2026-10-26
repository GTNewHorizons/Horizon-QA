package com.gtnewhorizons.horizonqa.api.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.awt.Point;
import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import net.minecraft.client.gui.GuiScreen;

import org.junit.Test;

import com.gtnewhorizons.horizonqa.client.ClientTaskQueue;
import com.gtnewhorizons.horizonqa.client.WindowDriver;

public class ClientWindowTest {

    @Test
    public void cancellingQueuedDragLeavesNativeCleanupToClientTeardown() throws Exception {
        ClientTaskQueue queue = new ClientTaskQueue();
        AtomicReference<Thread> releasedOn = new AtomicReference<>();
        WindowDriver driver = new WindowDriver() {

            public String description() {
                throw new AssertionError();
            }

            public boolean isOpen() {
                throw new AssertionError();
            }

            public GuiScreen screen() {
                throw new AssertionError();
            }

            public long renderedFrames() {
                throw new AssertionError();
            }

            public <T> T observe(Supplier<T> action) {
                throw new AssertionError();
            }

            public void move(GuiScreen screen, int x, int y) {
                throw new AssertionError();
            }

            public void mouse(GuiScreen screen, int x, int y, int button, boolean down) {
                throw new AssertionError();
            }

            public void scroll(GuiScreen screen, int x, int y, int delta) {
                throw new AssertionError();
            }

            public void key(GuiScreen screen, int key, char character, boolean down) {
                throw new AssertionError();
            }

            public void shift(GuiScreen screen, boolean down) {
                throw new AssertionError();
            }

            public void beginDrag(GuiScreen screen, int x, int y, int button) {
                throw new AssertionError();
            }

            public void dragMove(GuiScreen screen, int x, int y) {
                throw new AssertionError();
            }

            public boolean isDragging() {
                throw new AssertionError();
            }

            public void release() {
                releasedOn.set(Thread.currentThread());
            }

            public CompletableFuture<Void> inputConsumed() {
                throw new AssertionError();
            }

            public CompletableFuture<Void> resize(ClientTest owner, int width, int height) {
                throw new AssertionError();
            }

            public CompletableFuture<File> capture(ClientTest owner, String name) {
                throw new AssertionError();
            }

            public void close() {
                throw new AssertionError();
            }
        };
        ClientWindow window = new ClientWindow(null, queue, driver);
        AtomicInteger observations = new AtomicInteger();
        ClientTarget target = ClientTarget.of("queued handle", w -> {
            observations.incrementAndGet();
            return null;
        });
        CompletableFuture<Void> drag = window.drag(0, target, w -> new Point(10, 10), 4);
        Thread server = new Thread(queue::close, "server-test-teardown");
        server.start();
        server.join();
        assertTrue(drag.isCompletedExceptionally());
        assertEquals("A queued target must not be observed before client dispatch", 0, observations.get());
        assertNull("Server cancellation must not touch native input", releasedOn.get());
    }
}
