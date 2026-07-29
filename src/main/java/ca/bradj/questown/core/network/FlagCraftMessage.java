package ca.bradj.questown.core.network;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.init.items.ItemsInit;
import ca.bradj.questown.mc.Compat;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record FlagCraftMessage(BlockPos flagPos, int recipeIndex) {

    public static void encode(FlagCraftMessage msg, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(msg.flagPos);
        buffer.writeInt(msg.recipeIndex);
    }

    public static FlagCraftMessage decode(FriendlyByteBuf buffer) {
        return new FlagCraftMessage(buffer.readBlockPos(), buffer.readInt());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) {
                return;
            }
            switch (recipeIndex) {
                case 0 -> craftWand(sender);
                case 1 -> craftWelcomeMat(sender);
                default -> QT.GUI_LOGGER.error("Unknown flag craft recipe index: {}", recipeIndex);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private void craftWand(ServerPlayer player) {
        if (!consumeItem(player, Items.STICK)) {
            return;
        }
        giveAndNotify(player, new ItemStack(ItemsInit.TOWN_WAND.get()));
    }

    private void craftWelcomeMat(ServerPlayer player) {
        if (!consumeItem(player, Items.OAK_PRESSURE_PLATE)) {
            return;
        }
        giveAndNotify(player, new ItemStack(ItemsInit.WELCOME_MAT_BLOCK.get()));
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

    private boolean consumeItem(ServerPlayer player, Item required) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(required)) {
                stack.shrink(1);
                return true;
            }
        }
        QT.GUI_LOGGER.warn("Player {} tried to flag-craft but lacks the input item", player.getName().getString());
        return false;
    }
}
