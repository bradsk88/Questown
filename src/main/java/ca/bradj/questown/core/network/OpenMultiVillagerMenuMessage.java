package ca.bradj.questown.core.network;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.init.TilesInit;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import net.minecraft.core.BlockPos;
import ca.bradj.questown.loader.Channel;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

public record OpenMultiVillagerMenuMessage(
        int flagX, int flagY, int flagZ
) {

    public static void encode(OpenMultiVillagerMenuMessage msg, RegistryFriendlyByteBuf buffer) {
        buffer.writeInt(msg.flagX);
        buffer.writeInt(msg.flagY);
        buffer.writeInt(msg.flagZ);
    }

    public static OpenMultiVillagerMenuMessage decode(RegistryFriendlyByteBuf buffer) {
        int flagX = buffer.readInt();
        int flagY = buffer.readInt();
        int flagZ = buffer.readInt();
        return new OpenMultiVillagerMenuMessage(flagX, flagY, flagZ);
    }


    public void handle(
            Channel.Context ctx
    ) {
        ctx.enqueueWork(() -> {
            // Work that needs to be thread-safe (most work)
            ServerPlayer sender = (ServerPlayer) ctx.sender(); // the client that sent this packet
            // Do stuff
            if (!(sender.level().getBlockEntity(new BlockPos(flagX, flagY, flagZ)) instanceof TownFlagBlockEntity flag)) {
                QT.GUI_LOGGER.error("No flag at position {}, {}, {}. Quest will not be removed.", flagX, flagY, flagZ);
                return;
            }
            flag.getVillagerHandle().showMultiStatusUI(sender);
        });

    }
}
