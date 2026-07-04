package com.gtnewhorizons.horizonqa.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import com.gtnewhorizons.horizonqa.command.HorizonQACommand;
import com.gtnewhorizons.horizonqa.item.ItemHorizonWand;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class WandLabelMessage implements IMessage {

    private String name;
    private int x;
    private int y;
    private int z;

    @SuppressWarnings("unused")
    public WandLabelMessage() {}

    public WandLabelMessage(String name, int x, int y, int z) {
        this.name = name;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        name = ByteBufUtils.readUTF8String(buf);
        x = buf.readInt();
        y = buf.readInt();
        z = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, name == null ? "" : name);
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
    }

    public static final class Handler implements IMessageHandler<WandLabelMessage, IMessage> {

        @Override
        public IMessage onMessage(WandLabelMessage message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) {
                return null;
            }
            ItemStack held = player.getHeldItem();
            if (held == null || !(held.getItem() instanceof ItemHorizonWand)) {
                player.addChatMessage(
                    new ChatComponentText(EnumChatFormatting.RED + "Hold a Horizon Wand to create labels."));
                return null;
            }
            HorizonQACommand.applyLabel(player, held, message.name, message.x, message.y, message.z);
            player.inventoryContainer.detectAndSendChanges();
            return null;
        }
    }
}
