package ca.bradj.questown.core.network;

import ca.bradj.questown.core.init.AdvancementsInit;


import ca.bradj.questown.QT;
import ca.bradj.questown.blocks.TownFlagBlock;
import ca.bradj.questown.mc.Compat;
import net.minecraft.core.BlockPos;
import ca.bradj.questown.loader.Channel;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;


public record FlagCraftMessage(BlockPos flagPos, int recipeIndex) {

    public static void encode(FlagCraftMessage msg, RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(msg.flagPos);
        buffer.writeInt(msg.recipeIndex);
    }

    public static FlagCraftMessage decode(RegistryFriendlyByteBuf buffer) {
        return new FlagCraftMessage(buffer.readBlockPos(), buffer.readInt());
    }

    public void handle(Channel.Context ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer sender = (ServerPlayer) ctx.sender();
            if (sender == null) {
                return;
            }
            if (recipeIndex < 0 || recipeIndex >= FlagCraftRecipes.ALL.size()) {
                QT.GUI_LOGGER.error("Unknown flag craft recipe index: {}", recipeIndex);
                return;
            }
            FlagCraftRecipes.ALL.get(recipeIndex).craft(sender, flagPos);
        });
    }
}
