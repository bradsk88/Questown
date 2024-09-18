package ca.bradj.questown.core.network;

import ca.bradj.questown.core.init.items.ItemsInit;
import ca.bradj.questown.items.StockRequestItem;
import ca.bradj.questown.jobs.requests.WorkRequest;
import ca.bradj.questown.mc.Compat;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record CreateStockRequestFromUIMessage(
        WorkRequest item, int flagX, int flagY, int flagZ, UUID playerUUID
) {

    public static void encode(CreateStockRequestFromUIMessage msg, FriendlyByteBuf buffer) {
        msg.item.toNetwork(buffer);
        buffer.writeInt(msg.flagX());
        buffer.writeInt(msg.flagY());
        buffer.writeInt(msg.flagZ());
        buffer.writeUUID(msg.playerUUID());
    }

    public static CreateStockRequestFromUIMessage decode(FriendlyByteBuf buffer) {
        WorkRequest ing = WorkRequest.fromNetwork(buffer);
        int flagX = buffer.readInt();
        int flagY = buffer.readInt();
        int flagZ = buffer.readInt();
        UUID id = buffer.readUUID();
        return new CreateStockRequestFromUIMessage(ing, flagX, flagY, flagZ, id);
    }


    public void handle(
            Supplier<NetworkEvent.Context> ctx
    ) {
        ctx.get().enqueueWork(() -> {
            // Work that needs to be thread-safe (most work)
            ServerPlayer sender = ctx.get().getSender(); // the client that sent this packet
            if (sender.getInventory().getFreeSlot() == 0) {
                sender.sendMessage(Compat.translatable("no_room_for_request"), sender.getUUID());
                return;
            }
            ItemStack toGive = ItemsInit.STOCK_REQUEST.get().getDefaultInstance();
            StockRequestItem.writeToNBT(toGive.getOrCreateTag(), item);
            sender.getInventory().add(toGive);
        });
        ctx.get().setPacketHandled(true);

    }
}
