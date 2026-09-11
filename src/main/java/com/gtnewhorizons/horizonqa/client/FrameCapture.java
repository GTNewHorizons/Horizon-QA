package com.gtnewhorizons.horizonqa.client;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;

import javax.imageio.ImageIO;

import net.minecraft.client.Minecraft;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/** Reads a completed display frame on the render thread and encodes its artifact off-thread. */
public final class FrameCapture {

    private final File directory;

    public FrameCapture(File directory) {
        this.directory = directory;
    }

    public CompletableFuture<File> capture(String checkpoint) {
        Minecraft mc = Minecraft.getMinecraft();
        int width = mc.displayWidth;
        int height = mc.displayHeight;
        IntBuffer pixels = BufferUtils.createIntBuffer(width * height);
        GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        return CompletableFuture.supplyAsync(() -> {
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int color = pixels.get(y * width + x);
                    image.setRGB(x, height - y - 1, ((color & 255) << 16) | (color & 0xff00) | ((color >>> 16) & 255));
                }
            }
            File file = new File(directory, checkpoint + ".png");
            try {
                Files.createDirectories(directory.toPath());
                if (!ImageIO.write(image, "png", file)) throw new IllegalStateException("PNG encoder unavailable");
            } catch (IOException e) {
                throw new IllegalStateException("Cannot write screenshot " + file, e);
            }
            return file;
        });
    }
}
