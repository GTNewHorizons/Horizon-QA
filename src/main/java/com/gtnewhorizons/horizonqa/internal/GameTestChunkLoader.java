package com.gtnewhorizons.horizonqa.internal;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.ForgeChunkManager.Ticket;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.gtnewhorizons.horizonqa.HorizonQAMod;

public final class GameTestChunkLoader implements ForgeChunkManager.OrderedLoadingCallback {

    private static final Logger LOG = LogManager.getLogger("GameTest");

    private final List<Ticket> tickets = new ArrayList<>();

    public void forceChunks(World world, int x1, int y1, int z1, int x2, int y2, int z2) {
        Ticket ticket = ForgeChunkManager.requestTicket(HorizonQAMod.instance, world, ForgeChunkManager.Type.NORMAL);
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

        ChunkProviderServer cps = world.getChunkProvider() instanceof ChunkProviderServer
            ? (ChunkProviderServer) world.getChunkProvider()
            : null;

        for (int cx = chunkX1; cx <= chunkX2; cx++) {
            for (int cz = chunkZ1; cz <= chunkZ2; cz++) {
                if (cps != null) {
                    cps.loadChunk(cx, cz);
                }
                ForgeChunkManager.forceChunk(ticket, new ChunkCoordIntPair(cx, cz));
            }
        }
    }

    public void releaseAll() {
        for (Ticket t : tickets) {
            releaseTicketSafe(t);
        }
        tickets.clear();
    }

    private static void releaseTicketSafe(Ticket t) {
        if (t == null || t.world == null) return;
        try {
            ForgeChunkManager.releaseTicket(t);
        } catch (NullPointerException ex) {
            LOG.warn("[GameTest] Skipped Forge chunk ticket release (world/ticket no longer tracked by Forge).");
        }
    }

    @Override
    public void ticketsLoaded(List<Ticket> restoredTickets, World world) {
        for (Ticket t : restoredTickets) {
            releaseTicketSafe(t);
        }
    }

    @Override
    public List<Ticket> ticketsLoaded(List<Ticket> restoredTickets, World world, int maxTicketCount) {
        return new ArrayList<>();
    }
}
