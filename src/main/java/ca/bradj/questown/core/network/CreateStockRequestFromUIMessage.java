package ca.bradj.questown.core.network;

import ca.bradj.questown.core.init.items.ItemsInit;
import ca.bradj.questown.items.StockRequestItem;
import ca.bradj.questown.jobs.requests.WorkRequest;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.loader.Channel;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

public record CreateStockRequestFromUIMessage(
        WorkRequest item, int flagX, int flagY, int flagZ, UUID playerUUID
) {

    public static void encode(
            CreateStockRequestFromUIMessage msg,
            RegistryFriendlyByteBuf buffer
    ) {
        msg.item.toNetwork(buffer);
        buffer.writeInt(msg.flagX());
        buffer.writeInt(msg.flagY());
        buffer.writeInt(msg.flagZ());
        buffer.writeUUID(msg.playerUUID());
    }

    public static CreateStockRequestFromUIMessage decode(RegistryFriendlyByteBuf buffer) {
        WorkRequest ing = WorkRequest.fromNetwork(buffer);
        int flagX = buffer.readInt();
        int flagY = buffer.readInt();
        int flagZ = buffer.readInt();
        UUID id = buffer.readUUID();
        return new CreateStockRequestFromUIMessage(ing, flagX, flagY, flagZ, id);
    }


    public void handle(
            Channel.Context ctx
    ) {
        ctx.enqueueWork(() -> {
            // Work that needs to be thread-safe (most work)
            ServerPlayer sender = (ServerPlayer) ctx.sender(); // the client that sent this packet
            if (sender.getInventory().getFreeSlot() == 0) {
                Compat.sendMessage(sender, Compat.translatable("no_room_for_request"));
                return;
            }
            ItemStack toGive = ItemsInit.STOCK_REQUEST.get().getDefaultInstance();
            net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, toGive, tag -> StockRequestItem.writeToNBT(tag, item));
            ItemStack given = new ItemStack(toGive.getItem(), toGive.getCount());
            sender.getInventory().add(toGive);
            QuestownNetwork.sendToPlayer(sender, new CloseScreensMessage());
            sender.containerMenu.broadcastChanges();
            Compat.sendMessage(sender, Compat.translatable(
                    "commands.give.success.single",
                    1, given.getDisplayName(), (sender).getDisplayName()
            ));
        });

    }
}
