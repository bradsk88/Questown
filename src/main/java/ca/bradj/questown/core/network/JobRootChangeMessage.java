package ca.bradj.questown.core.network;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.init.TilesInit;
import ca.bradj.questown.gui.BopTransactionSyncer;
import ca.bradj.questown.town.TownFlagBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public record JobRootChangeMessage(BlockPos flagPos, UUID villagerUUID, boolean instant) {

    public static void encode(
            JobRootChangeMessage msg,
            FriendlyByteBuf buffer
    ) {
        buffer.writeBlockPos(msg.flagPos);
        buffer.writeUUID(msg.villagerUUID);
        buffer.writeBoolean(msg.instant());
    }

    public static JobRootChangeMessage decode(FriendlyByteBuf buffer) {
        BlockPos flagPos = buffer.readBlockPos();
        UUID uuid = buffer.readUUID();
        boolean instant = buffer.readBoolean();
        return new JobRootChangeMessage(flagPos, uuid, instant);
    }

    public void handle(
            Supplier<NetworkEvent.Context> ctx
    ) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            Optional<TownFlagBlockEntity> flag = sender.getLevel().getBlockEntity(flagPos, TilesInit.TOWN_FLAG.get());
            if (flag.isEmpty()) {
                QT.GUI_LOGGER.error("No flag at position {}. Job root will not be changed.", flagPos);
                return;
            }
            BopTransactionSyncer.syncConsumedBOP(sender);
            flag.get().getVillagerHandle().scheduleJobRootChange(villagerUUID, instant);
        });
        ctx.get().setPacketHandled(true);

    }
}
