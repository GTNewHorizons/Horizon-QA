package com.gtnewhorizons.horizonqa.network;

import com.gtnewhorizons.horizonqa.HorizonQAMod;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

public final class HorizonQANetwork {

    public static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(HorizonQAMod.MODID);

    private HorizonQANetwork() {}

    public static void init() {
        CHANNEL.registerMessage(WandLabelMessage.Handler.class, WandLabelMessage.class, 0, Side.SERVER);
    }
}
