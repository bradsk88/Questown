package ca.bradj.questown.core.network;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.init.TilesInit;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.Jobs;
import ca.bradj.questown.loader.Channel;
import ca.bradj.questown.town.TownVillagerUIs;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.UUID;

/** A skill-tree click: asks the server for that skill's details screen (dev playtest 2026-10-29). */
public record OpenSkillDetailsMessage(
        BlockPos flagPos,
        UUID villager,
        JobID job,
        boolean locked
) {

    public static void encode(
            OpenSkillDetailsMessage msg,
            RegistryFriendlyByteBuf buffer
    ) {
        buffer.writeBlockPos(msg.flagPos);
        buffer.writeUUID(msg.villager);
        Jobs.writeIdToNetwork(buffer, msg.job);
        buffer.writeBoolean(msg.locked);
    }

    public static OpenSkillDetailsMessage decode(RegistryFriendlyByteBuf buffer) {
        return new OpenSkillDetailsMessage(
                buffer.readBlockPos(), buffer.readUUID(), Jobs.getIdFromNetwork(buffer), buffer.readBoolean()
        );
    }

    public void handle(
            Channel.Context ctx
    ) {
        ctx.enqueueWork(() -> {
            ServerPlayer sender = (ServerPlayer) ctx.sender();
            Optional<TownFlagBlockEntity> flag = sender.level().getBlockEntity(flagPos, TilesInit.TOWN_FLAG.get());
            if (flag.isEmpty()) {
                QT.GUI_LOGGER.error("No flag at position {}. Skill details not shown.", flagPos);
                return;
            }
            TownVillagerUIs.showSkillDetailsUI(
                    sender, flag.get(), flag.get().getVillagerHandle().entities(), job, villager, locked
            );
        });
    }
}
