package ca.bradj.questown.core.network;

import ca.bradj.questown.core.VillagerUUID;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.init.TilesInit;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.Jobs;
import ca.bradj.questown.mc.Compat;
import com.google.common.collect.ImmutableList;
import net.minecraft.network.chat.Component;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import net.minecraft.core.BlockPos;
import ca.bradj.questown.loader.Channel;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.UUID;

public record ChangeVillagerJobMessage(
        int flagX, int flagY, int flagZ, UUID villagerUUID, JobID newJob, boolean announce,
        /** The skill-tree confirm's "after this trip" (dev playtest 2026-10-29); false = now. */
        boolean afterTrip
) {

    public static void encode(
            ChangeVillagerJobMessage msg,
            FriendlyByteBuf buffer
    ) {
        buffer.writeInt(msg.flagX());
        buffer.writeInt(msg.flagY());
        buffer.writeInt(msg.flagZ());
        buffer.writeUUID(msg.villagerUUID());
        NetworkCompat.toNetwork(buffer, msg.newJob());
        buffer.writeBoolean(msg.announce());
        buffer.writeBoolean(msg.afterTrip());
    }

    public static ChangeVillagerJobMessage decode(FriendlyByteBuf buffer) {
        int flagX = buffer.readInt();
        int flagY = buffer.readInt();
        int flagZ = buffer.readInt();
        UUID id = buffer.readUUID();
        JobID job = NetworkCompat.fromNetworkJobID(buffer);
        boolean announce1 = buffer.readBoolean();
        boolean afterTrip = buffer.readBoolean();
        return new ChangeVillagerJobMessage(flagX, flagY, flagZ, id, job, announce1, afterTrip);
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
            // The UUID wire field predates VillagerUUID; from() is the conversion the board cascade uses too.
            flag.getVillagerHandle().switchJob(VillagerUUID.from(villagerUUID), newJob, afterTrip);
            if (afterTrip) {
                // Dev 2026-10-30: the choice gave no feedback; the villager card notes it too.
                ImmutableList<Component> jc = Jobs.getComponentsForTooltip(newJob);
                Compat.sendMessage(sender, Compat.translatable("message.questown.switch_after_trip",
                        flag.getVillagerHandle().labelFor(villagerUUID),
                        Compat.translatable("menu.common.job_name", jc.get(0), jc.get(1))));
            }
        });

    }
}
