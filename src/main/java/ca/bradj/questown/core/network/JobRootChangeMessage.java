package ca.bradj.questown.core.network;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.init.TilesInit;
import ca.bradj.questown.gui.BopTransactionSyncer;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import ca.bradj.questown.loader.Channel;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.UUID;

public record JobRootChangeMessage(BlockPos flagPos, UUID villagerUUID, boolean instant,
                                   /** Spend the flag's oldest Block of Progress, not the slot's (opened from the flag). */
                                   boolean fromFlag) {

    public static void encode(
            JobRootChangeMessage msg,
            RegistryFriendlyByteBuf buffer
    ) {
        buffer.writeBlockPos(msg.flagPos);
        buffer.writeUUID(msg.villagerUUID);
        buffer.writeBoolean(msg.instant());
        buffer.writeBoolean(msg.fromFlag());
    }

    public static JobRootChangeMessage decode(RegistryFriendlyByteBuf buffer) {
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
            if (!(sender.level().getBlockEntity(flagPos) instanceof TownFlagBlockEntity flag)) {
                QT.GUI_LOGGER.error("No flag at position {}. Job root will not be changed.", flagPos);
                return;
            }
            // Read the consumed BOP's guarantee before it vanishes: a blessed ("gold") BOP
            // forces the specified root instead of the usual random choice.
            ItemStack consumed;
            if (fromFlag) {
                Optional<ItemStack> taken = flag.takeOldestBop();
                if (taken.isEmpty()) {
                    QT.GUI_LOGGER.info("Flag has no Block of Progress; job root of {} not changed", villagerUUID);
                    return;
                }
                consumed = taken.get();
            } else {
                consumed = BopTransactionSyncer.syncConsumedBOP(sender);
            }
            Optional<JobID> guarantee = ca.bradj.questown.blocks.BlockOfProgress.getBlessedJob(consumed);
            // The flag owns the spend-and-request: it schedules the root change (deciding the
            // root now) and completes any matching JOB_CHANGE quest at the spend moment.
            flag.requestJobRootChange(villagerUUID, instant, guarantee);
        });

    }
}
