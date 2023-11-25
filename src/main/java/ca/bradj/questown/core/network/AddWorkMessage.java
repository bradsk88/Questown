package ca.bradj.questown.core.network;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.init.TilesInit;
import ca.bradj.questown.town.TownFlagBlockEntity;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.network.NetworkEvent;

import java.util.Optional;
import java.util.function.Supplier;

public record AddWorkMessage(
        ItemStack requested,
        int flagX, int flagY, int flagZ
) {

    public static void encode(AddWorkMessage msg, FriendlyByteBuf buffer) {
        buffer.writeItem(msg.requested);
        buffer.writeInt(msg.flagX);
        buffer.writeInt(msg.flagY);
        buffer.writeInt(msg.flagZ);

    }

    public static AddWorkMessage decode(FriendlyByteBuf buffer) {
        ItemStack requested = buffer.readItem();
        int flagX = buffer.readInt();
        int flagY = buffer.readInt();
        int flagZ = buffer.readInt();
        return new AddWorkMessage(requested, flagX, flagY, flagZ);
    }


    public void handle(
            Supplier<NetworkEvent.Context> ctx
    ) {
        ctx.get().enqueueWork(() -> {
            // Work that needs to be thread-safe (most work)
            ServerPlayer sender = ctx.get().getSender(); // the client that sent this packet
            // Do stuff
            Optional<TownFlagBlockEntity> flag = sender.getLevel()
                    .getBlockEntity(new BlockPos(flagX, flagY, flagZ), TilesInit.TOWN_FLAG.get());
            if (flag.isEmpty()) {
                QT.GUI_LOGGER.error("No flag at position {}, {}, {}. Work will not be added.", flagX, flagY, flagZ);
                return;
            }
            flag.get().requestResult(ImmutableList.of(Ingredient.of(requested)));
        });
        ctx.get().setPacketHandled(true);

    }
}
