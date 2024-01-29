package ca.bradj.questown.core.network;

import ca.bradj.questown.blocks.entity.PlateBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record SyncBlockItemMessage(
        BlockPos pos,
        ItemStack item
) {

    public static void encode(SyncBlockItemMessage msg, FriendlyByteBuf buffer) {
        buffer.writeInt(msg.pos.getX());
        buffer.writeInt(msg.pos.getY());
        buffer.writeInt(msg.pos.getZ());
        buffer.writeItem(msg.item());
    }

    public static SyncBlockItemMessage decode(FriendlyByteBuf buffer) {
        int blockX = buffer.readInt();
        int blockY = buffer.readInt();
        int blockZ = buffer.readInt();
        ItemStack item = buffer.readItem();
        return new SyncBlockItemMessage(new BlockPos(blockX, blockY, blockZ), item);
    }


    public void handle(
            Supplier<NetworkEvent.Context> ctx
    ) {
        ctx.get().enqueueWork(() -> {
            if (Minecraft.getInstance().level.getBlockEntity(pos()) instanceof PlateBlockEntity be) {
                be.setFood(item);
            }
        });
        ctx.get().setPacketHandled(true);

    }
}
