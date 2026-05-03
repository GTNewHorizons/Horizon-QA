package com.gtnewhorizons.gametest.core;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.ForgeChunkManager.Ticket;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.gtnewhorizons.gametest.GameTestMod;

/**
 * Holds {@link ForgeChunkManager} tickets for all active test cells so that multiblocks spanning
 * chunk borders don't unload mid-simulation.
 *
 * <p>
 * Usage:
 * <ol>
 * <li>Register once in preInit via
 * {@link ForgeChunkManager#setForcedChunkLoadingCallback}.</li>
 * <li>Call {@link #forceChunks(World, int, int, int, int, int, int)} for each test cell's
 * bounding box before the test starts.</li>
 * <li>Call {@link #releaseAll()} when the run completes to return all tickets.</li>
 * </ol>
 *
 * <p>
 * Implements {@link ForgeChunkManager.OrderedLoadingCallback} so that Forge correctly
 * re-validates any persisted tickets on server restart (we release them all, since test cells are
 * ephemeral).
 */
public final class GameTestChunkLoader implements ForgeChunkManager.OrderedLoadingCallback {

    private static final Logger LOG = LogManager.getLogger("GameTest");

    private final List<Ticket> tickets = new ArrayList<>();

    /**
     * Force-load all chunks that intersect the bounding box defined by the two world-space corner
     * block coordinates.
     *
     * @param world the dimension to ticket
     * @param x1    min block X (inclusive)
     * @param y1    min block Y (for logging only — chunk coords are 2-D)
     * @param z1    min block Z (inclusive)
     * @param x2    max block X (inclusive)
     * @param y2    max block Y (for logging only)
     * @param z2    max block Z (inclusive)
     */
    public void forceChunks(World world, int x1, int y1, int z1, int x2, int y2, int z2) {
        Ticket ticket = ForgeChunkManager.requestTicket(GameTestMod.instance, world, ForgeChunkManager.Type.NORMAL);
        if (ticket == null) {
            LOG.warn(
                "ForgeChunkManager refused ticket for bounding box "
                    + "({},{},{})→({},{},{}) — chunks may unload during test",
                x1,
                y1,
                z1,
                x2,
                y2,
                z2);
            return;
        }
        tickets.add(ticket);

        int chunkX1 = x1 >> 4;
        int chunkZ1 = z1 >> 4;
        int chunkX2 = x2 >> 4;
        int chunkZ2 = z2 >> 4;

        for (int cx = chunkX1; cx <= chunkX2; cx++) {
            for (int cz = chunkZ1; cz <= chunkZ2; cz++) {
                ForgeChunkManager.forceChunk(ticket, new ChunkCoordIntPair(cx, cz));
            }
        }
    }

    /** Release all tickets acquired during this run. Call once after the final batch completes. */
    public void releaseAll() {
        for (Ticket t : tickets) {
            releaseTicketSafe(t);
        }
        tickets.clear();
    }

    /**
     * {@link ForgeChunkManager#releaseTicket} assumes {@code Ticket.world} is still a key in Forge's
     * internal ticket map (e.g. after a dimension unload / map rebuild that can disagree with stale
     * references). Calling it then throws {@link NullPointerException}; skip cleanly so commands
     * like {@code /gametest clearall} do not abort.
     */
    private static void releaseTicketSafe(Ticket t) {
        if (t == null || t.world == null) return;
        try {
            ForgeChunkManager.releaseTicket(t);
        } catch (NullPointerException ex) {
            LOG.warn("[GameTest] Skipped Forge chunk ticket release (world/ticket no longer tracked by Forge).");
        }
    }

    // -------------------------------------------------------------------------
    // OrderedLoadingCallback — ticket restoration on server restart
    // -------------------------------------------------------------------------

    @Override
    public void ticketsLoaded(List<Ticket> restoredTickets, World world) {
        // Test cells are ephemeral — release any tickets that survived a crash/restart.
        for (Ticket t : restoredTickets) {
            releaseTicketSafe(t);
        }
    }

    @Override
    public List<Ticket> ticketsLoaded(List<Ticket> restoredTickets, World world, int maxTicketCount) {
        // Return an empty list so Forge knows we want zero tickets restored.
        return new ArrayList<>();
    }
}
