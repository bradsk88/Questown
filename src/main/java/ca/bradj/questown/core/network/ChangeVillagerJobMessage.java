package ca.bradj.questown.core.network;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.init.TilesInit;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import net.minecraft.core.BlockPos;
import ca.bradj.questown.loader.Channel;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.UUID;

public record ChangeVillagerJobMessage(
        int flagX, int flagY, int flagZ, UUID villagerUUID, JobID newJob, boolean announce
) {

    public static void encode(
            ChangeVillagerJobMessage msg,
            RegistryFriendlyByteBuf buffer
    ) {
        buffer.writeInt(msg.flagX());
        buffer.writeInt(msg.flagY());
        buffer.writeInt(msg.flagZ());
        buffer.writeUUID(msg.villagerUUID());
        NetworkCompat.toNetwork(buffer, msg.newJob());
        buffer.writeBoolean(msg.announce());
    }

    public static ChangeVillagerJobMessage decode(RegistryFriendlyByteBuf buffer) {
        int flagX = buffer.readInt();
        int flagY = buffer.readInt();
        int flagZ = buffer.readInt();
        UUID id = buffer.readUUID();
        JobID job = NetworkCompat.fromNetworkJobID(buffer);
        boolean announce1 = buffer.readBoolean();
        return new ChangeVillagerJobMessage(flagX, flagY, flagZ, id, job, announce1);
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
            flag.getVillagerHandle().changeJobForVillager(villagerUUID, newJob, announce);
        });

    }
}
