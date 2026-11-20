package com.gtnewhorizons.horizonqa.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;

import javax.annotation.Nonnull;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.WorldServer;

import com.gtnewhorizons.horizonqa.internal.GameTestRunner;

/**
 * A developer-owned laboratory with real world ticks, physical controls and no test verdict or execution timeout.
 * Setup and callbacks run on the server thread. Register restoration before mutating fixtures or global state.
 */
public final class ManualSession implements AutoCloseable {

    private final WorldServer world;
    private final EntityPlayerMP player;
    private final GameTestRunner runner = new GameTestRunner();
    private final Deque<Runnable> cleanup = new ArrayDeque<>();
    private final List<Runnable> ticks = new ArrayList<>();
    private boolean closed;

    private ManualSession(WorldServer world, EntityPlayerMP player) {
        this.world = world;
        this.player = player;
    }

    /** Acquires execution before setup. Call on the server thread after the integrated player is ready. */
    @Nonnull
    public static ManualSession open(@Nonnull WorldServer world, @Nonnull EntityPlayerMP player,
        @Nonnull Consumer<ManualSession> setup) {
        ManualSession session = new ManualSession(world, player);
        if (!session.runner.tryStartManual(() -> {
            setup.accept(session);
            session.runner.onManualTick(session::tick);
        }, session::restore)) {
            throw new IllegalStateException("Another Horizon-QA execution already owns the world");
        }
        return session;
    }

    @Nonnull
    public WorldServer world() {
        return world;
    }

    @Nonnull
    public EntityPlayerMP player() {
        return player;
    }

    /** Registers restoration before a mutation, in reverse setup order. Also runs after failed setup. */
    public void onClose(@Nonnull Runnable action) {
        requireOpen();
        cleanup.addFirst(action);
    }

    /** Registers a supply callback after each world tick. Machine controllers retain processing and validation. */
    public void onTick(@Nonnull Runnable action) {
        requireOpen();
        ticks.add(action);
    }

    private void tick() {
        int count = ticks.size();
        for (int i = 0; !closed && i < count; i++) ticks.get(i)
            .run();
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("Manual laboratory is closed");
    }

    private void restore() {
        if (closed) return;
        closed = true;
        ticks.clear();
        Throwable failure = null;
        while (!cleanup.isEmpty()) {
            try {
                cleanup.removeFirst()
                    .run();
            } catch (RuntimeException | Error thrown) {
                if (failure == null) failure = thrown;
                else if (failure != thrown) failure.addSuppressed(thrown);
            }
        }
        if (failure instanceof RuntimeException) throw (RuntimeException) failure;
        if (failure instanceof Error) throw (Error) failure;
    }

    @Override
    public void close() {
        runner.closeManual();
    }

    /** Marks a public static void setup(ManualSession) method inside a GameTestHolder. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface Setup {}
}
