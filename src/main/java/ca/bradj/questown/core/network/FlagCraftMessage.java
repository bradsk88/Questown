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
            craft(sender, FlagCraftRecipes.ALL.get(recipeIndex));
        });
    }

    private void craft(ServerPlayer player, FlagCraftRecipes recipe) {
        if (!consumeMatching(player, recipe.input().get())) {
            return;
        }
        // Every output only works against its parent flag (wand actions, mat / board / room-block
        // registration all resolve it through TownFlagBlock.GetParentFromNBT), so stamp the exact
        // flag the player crafted at.
        ItemStack crafted = new ItemStack(recipe.output().get());
        TownFlagBlock.StoreParentOnNBT(crafted, flagPos);
        giveAndNotify(player, crafted);
        if (recipe.advancement() != null) {
            AdvancementsInit.ROOM_TRIGGER.trigger(player, recipe.advancement());
        }
    }

    // The "Created X" message is the only confirmation a craft gives, so it must not be able to lie:
    // a full inventory would otherwise swallow the item while the input was still consumed. Drop the
    // remainder at the player's feet instead, the same fallback the flag uses for a block of progress.
    private void giveAndNotify(ServerPlayer player, ItemStack crafted) {
        // The display name must be captured BEFORE add(): on success the stack is
        // consumed (count -> 0), and an empty stack's getItem() falls back to
        // Items.AIR (vanilla behavior) - that fallback is what made the message read
        // "Created Air" while the real item landed in the inventory (2026-10-11 report,
        // welcome-mat-craft-chat-says-created-air.md).
        Component itemName = crafted.getHoverName();
        if (!player.getInventory().add(crafted)) {
            player.drop(crafted, false);
        }
        player.sendSystemMessage(Compat.translatable("message.flag_crafting.created", itemName));
    }

    private boolean consumeMatching(ServerPlayer player, Ingredient required) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (required.test(stack)) {
                stack.shrink(1);
                return true;
            }
        }
        QT.GUI_LOGGER.warn("Player {} tried to flag-craft but lacks the input item", player.getName().getString());
        return false;
    }
}
