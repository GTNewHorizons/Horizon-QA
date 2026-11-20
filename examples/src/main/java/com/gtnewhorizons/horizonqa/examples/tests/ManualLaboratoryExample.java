package com.gtnewhorizons.horizonqa.examples.tests;

import net.minecraft.init.Blocks;

import com.gtnewhorizons.horizonqa.api.ManualSession;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;

/** A runnable laboratory that remains open until the developer uses /qa close or leaves the world. */
@GameTestHolder(value = "horizonqaexamples", clientOnly = true)
public final class ManualLaboratoryExample {

    private ManualLaboratoryExample() {}

    @ManualSession.Setup
    public static void prepare(ManualSession session) {
        int x = (int) session.player().posX + 3;
        int y = (int) session.player().posY;
        int z = (int) session.player().posZ;
        session.onClose(
            () -> session.world()
                .setBlockToAir(x, y, z));
        session.world()
            .setBlock(x, y, z, Blocks.gold_block);
    }
}
