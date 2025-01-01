package ca.bradj.questown.core.network;

import ca.bradj.questown.blocks.entity.ItemAccepting;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record SyncBlockItemMessage(
        BlockPos pos,
        ItemStack item,
        int index
) {

    public static void encode(SyncBlockItemMessage msg, FriendlyByteBuf buffer) {
        buffer.writeInt(msg.pos.getX());
        buffer.writeInt(msg.pos.getY());
        buffer.writeInt(msg.pos.getZ());
        buffer.writeItem(msg.item());
        buffer.writeInt(msg.index());
    }

    public static SyncBlockItemMessage decode(FriendlyByteBuf buffer) {
        int blockX = buffer.readInt();
        int blockY = buffer.readInt();
        int blockZ = buffer.readInt();
        ItemStack item = buffer.readItem();
        int index = buffer.readInt();
        return new SyncBlockItemMessage(new BlockPos(blockX, blockY, blockZ), item, index);
    }


    public void handle(
            Supplier<NetworkEvent.Context> ctx
    ) {
        ctx.get().enqueueWork(() -> {
            if (Minecraft.getInstance().level.getBlockEntity(pos()) instanceof ItemAccepting be) {
                be.setItem(index, MCTownItem.fromMCItemStack(item));
            }
        });
        ctx.get().setPacketHandled(true);

    }
}
