package ca.bradj.questown.core.network;

import ca.bradj.questown.QT;
import ca.bradj.questown.gui.BopTransactionSyncer;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import net.minecraft.core.BlockPos;
import ca.bradj.questown.loader.Channel;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.UUID;

public record JobRootChangeMessage(BlockPos flagPos, UUID villagerUUID, boolean instant,
                                   /** Spend the flag's oldest Block of Progress, not the slot's (opened from the flag). */
                                   boolean fromFlag) {

    public static void encode(
            JobRootChangeMessage msg,
            FriendlyByteBuf buffer
    ) {
        buffer.writeBlockPos(msg.flagPos);
        buffer.writeUUID(msg.villagerUUID);
        buffer.writeBoolean(msg.instant());
        buffer.writeBoolean(msg.fromFlag());
    }

    public static JobRootChangeMessage decode(FriendlyByteBuf buffer) {
        BlockPos flagPos = buffer.readBlockPos();
        UUID uuid = buffer.readUUID();
        boolean instant = buffer.readBoolean();
        return new JobRootChangeMessage(flagPos, uuid, instant, buffer.readBoolean());
    }

    public void handle(
            Channel.Context ctx
    ) {
        ctx.enqueueWork(() -> {
            ServerPlayer sender = (ServerPlayer) ctx.sender();
            if (!(sender.getLevel().getBlockEntity(flagPos) instanceof TownFlagBlockEntity flag)) {
                QT.GUI_LOGGER.error("No flag at position {}. Job root will not be changed.", flagPos);
                return;
            }
            // A BOP guarantees no root: an open job-change quest decides it in
            // requestJobRootChange, else it is random.
            if (fromFlag) {
                if (flag.takeOldestBop().isEmpty()) {
                    QT.GUI_LOGGER.info("Flag has no Block of Progress; job root of {} not changed", villagerUUID);
                    return;
                }
            } else {
                BopTransactionSyncer.syncConsumedBOP(sender);
            }
            flag.requestJobRootChange(villagerUUID, instant, Optional.empty());
        });

    }
}
