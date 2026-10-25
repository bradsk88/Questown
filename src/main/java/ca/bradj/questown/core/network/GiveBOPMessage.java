package ca.bradj.questown.core.network;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.init.TilesInit;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import net.minecraft.core.BlockPos;
import ca.bradj.questown.loader.Channel;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

public record GiveBOPMessage(BlockPos flagPos) {

    public static void encode(
            GiveBOPMessage msg,
            RegistryFriendlyByteBuf buffer
    ) {
        buffer.writeBlockPos(msg.flagPos);
    }

    public static GiveBOPMessage decode(RegistryFriendlyByteBuf buffer) {
        BlockPos flagPos = buffer.readBlockPos();
        return new GiveBOPMessage(flagPos);
    }

    public void handle(
            Channel.Context ctx
    ) {
        ctx.enqueueWork(() -> {
            // Work that needs to be thread-safe (most work)
            ServerPlayer sender = (ServerPlayer) ctx.sender(); // the client that sent this packet
            // Do stuff
            if (!(sender.level().getBlockEntity(flagPos) instanceof TownFlagBlockEntity flag)) {
                QT.GUI_LOGGER.error("No flag at position {}. Quest will not be removed.", flagPos);
                return;
            }
            flag.ejectBlockOfProgress(sender);
            // Re-open the tab with the new count instead of the client closing it (r17 #11).
            flag.menus.showUI(sender, OpenFlagMenuMessage.BOP, flag.getInfo(), flag.getBlocksOfProgress());
        });

    }
}
