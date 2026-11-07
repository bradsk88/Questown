package ca.bradj.questown.core.network;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.init.TilesInit;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import net.minecraft.core.BlockPos;
import ca.bradj.questown.loader.Channel;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

public record GiveBOPMessage(BlockPos flagPos) {

    public static void encode(
            GiveBOPMessage msg,
            FriendlyByteBuf buffer
    ) {
        buffer.writeBlockPos(msg.flagPos);
    }

    public static GiveBOPMessage decode(FriendlyByteBuf buffer) {
        BlockPos flagPos = buffer.readBlockPos();
        return new GiveBOPMessage(flagPos);
    }

    public void handle(
            Channel.Context ctx
    ) {
        ctx.enqueueWork(() -> {
            // Work that needs to be thread-safe (most work)
            ServerPlayer sender = (ServerPlayer) ctx.sender();
            // Do stuff
            if (!(sender.getLevel().getBlockEntity(flagPos) instanceof TownFlagBlockEntity flag)) {
                QT.GUI_LOGGER.error("No flag at position {}. Quest will not be removed.", flagPos);
                return;
            }
            flag.ejectBlockOfProgress(sender);
            flag.menus.showUI(sender, OpenFlagMenuMessage.BOP, flag.getInfo(), flag.getBlocksOfProgress());
        });

    }
}
