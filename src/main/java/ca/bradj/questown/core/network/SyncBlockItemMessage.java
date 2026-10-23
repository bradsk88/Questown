package ca.bradj.questown.core.network;

import ca.bradj.questown.blocks.entity.ItemAccepting;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import ca.bradj.questown.loader.Channel;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;


public record SyncBlockItemMessage(
        BlockPos pos,
        ItemStack item,
        int index
) {

    public static void encode(SyncBlockItemMessage msg, RegistryFriendlyByteBuf buffer) {
        buffer.writeInt(msg.pos.getX());
        buffer.writeInt(msg.pos.getY());
        buffer.writeInt(msg.pos.getZ());
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, msg.item());
        buffer.writeInt(msg.index());
    }

    public static SyncBlockItemMessage decode(RegistryFriendlyByteBuf buffer) {
        int blockX = buffer.readInt();
        int blockY = buffer.readInt();
        int blockZ = buffer.readInt();
        ItemStack item = ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer);
        int index = buffer.readInt();
        return new SyncBlockItemMessage(new BlockPos(blockX, blockY, blockZ), item, index);
    }


    @SuppressWarnings({"rawtypes", "unchecked"})
    public void handle(
            Channel.Context ctx
    ) {
        ctx.enqueueWork(() -> {
            if (Minecraft.getInstance().level.getBlockEntity(pos()) instanceof ItemAccepting be) {
                be.setItem(index, MCTownItem.fromMCItemStack(item));
            }
        });

    }
}
