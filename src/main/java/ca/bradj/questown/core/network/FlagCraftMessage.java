package ca.bradj.questown.core.network;

import java.util.List;

import net.minecraft.resources.ResourceLocation;

import ca.bradj.questown.town.entity.TownFlagBlockEntity;

import ca.bradj.questown.gui.FlagCraftGates;

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
            FlagCraftRecipes recipe = FlagCraftRecipes.ALL.get(recipeIndex);
            // The screen hides a gated row until its quest is posted; refuse it by index too (r17 #1).
            if (recipe.unlockedByQuest() != null
                    && sender.level().getBlockEntity(flagPos) instanceof TownFlagBlockEntity flag) {
                List<ResourceLocation> posted = flag.getQuestHandle().getAllQuestsWithRewards().stream()
                        .map(e -> e.getKey().getWantedId())
                        .toList();
                if (!FlagCraftGates.visible(recipe.unlockedByQuest(), posted)) {
                    QT.GUI_LOGGER.warn("Flag craft {} refused: its quest has not been posted", recipeIndex);
                    return;
                }
            }
            recipe.craft(sender, flagPos);
        });
    }
}
