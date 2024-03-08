package ca.bradj.questown.core.network;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.init.TilesInit;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.town.TownFlagBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public record ChangeVillagerJobMessage(
        int flagX, int flagY, int flagZ, UUID villagerUUID, JobID newJob, boolean announce
) {

    public static void encode(ChangeVillagerJobMessage msg, FriendlyByteBuf buffer) {
        buffer.writeInt(msg.flagX());
        buffer.writeInt(msg.flagY());
        buffer.writeInt(msg.flagZ());
        buffer.writeUUID(msg.villagerUUID());
        buffer.writeResourceLocation(msg.newJob().id());
        buffer.writeBoolean(msg.announce());
    }

    public static ChangeVillagerJobMessage decode(FriendlyByteBuf buffer) {
        int flagX = buffer.readInt();
        int flagY = buffer.readInt();
        int flagZ = buffer.readInt();
        UUID id = buffer.readUUID();
        ResourceLocation resourceLocation = buffer.readResourceLocation();
        boolean announce1 = buffer.readBoolean();
        return new ChangeVillagerJobMessage(flagX, flagY, flagZ, id, JobID.fromRL(resourceLocation), announce1);
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
                QT.GUI_LOGGER.error("No flag at position {}, {}, {}. Quest will not be removed.", flagX, flagY, flagZ);
                return;
            }
            flag.get().getVillagerHandle().changeJobForVisitor(villagerUUID, newJob, announce);
        });
        ctx.get().setPacketHandled(true);

    }
}
