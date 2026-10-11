package ca.bradj.questown.core.network;

import ca.bradj.questown.QT;
import ca.bradj.questown.blocks.TownFlagBlock;
import ca.bradj.questown.core.init.items.ItemsInit;
import ca.bradj.questown.mc.Compat;
import net.minecraft.core.BlockPos;
import ca.bradj.questown.loader.Channel;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
            switch (recipeIndex) {
                case 0 -> craftWand(sender);
                case 1 -> craftWelcomeMat(sender);
                default -> QT.GUI_LOGGER.error("Unknown flag craft recipe index: {}", recipeIndex);
            }
        });
    }

    private void craftWand(ServerPlayer player) {
        if (!consumeMatching(player, Ingredient.of(Items.STICK))) {
            return;
        }
        // A wand is only as good as its parent flag: every action resolves the
        // parent through GetParentFromNBT, so an unstamped wand is inert (its
        // tooltip reads "inactive"). Stamp the exact flag crafted at, the same
        // parent-stamping the welcome mat carries.
        ItemStack wand = new ItemStack(ItemsInit.TOWN_WAND.get());
        TownFlagBlock.StoreParentOnNBT(wand, flagPos);
        giveAndNotify(player, wand);
    }

    private void craftWelcomeMat(ServerPlayer player) {
        // Any wooden pressure plate is a valid input (the player may hold spruce,
        // birch, ...); matching only OAK rejected the plates they actually carry.
        if (!consumeMatching(player, Ingredient.of(ItemTags.WOODEN_PRESSURE_PLATES))) {
            return;
        }
        // A mat only works when placed against its parent flag: placement reads the
        // questown.parent_pos_* NBT (TownFlagBlock.GetParentFromNBT) and drops the item
        // (unregistered) when it is missing. The craft happens at a flag, so stamp the
        // exact flag the player crafted at - the same parent-stamping the flag's own
        // child-block drops carry (TownFlagBlock.StoreParentOnNBT).
        ItemStack mat = new ItemStack(ItemsInit.WELCOME_MAT_BLOCK.get());
        TownFlagBlock.StoreParentOnNBT(mat, flagPos);
        giveAndNotify(player, mat);
    }

    // The "Created X" message is the only confirmation a craft gives, so it must not be able to lie:
    // a full inventory would otherwise swallow the item while the input was still consumed. Drop the
    // remainder at the player's feet instead, the same fallback the flag uses for a block of progress.
    private void giveAndNotify(ServerPlayer player, ItemStack crafted) {
        if (!player.getInventory().add(crafted)) {
            player.drop(crafted, false);
        }
        player.sendSystemMessage(Compat.translatable("message.flag_crafting.created", crafted.getHoverName()));
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
