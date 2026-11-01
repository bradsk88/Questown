package ca.bradj.questown.core.network;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.init.TilesInit;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.Jobs;
import ca.bradj.questown.loader.Channel;
import ca.bradj.questown.town.TownVillagerUIs;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.UUID;

/** A skill-tree click: asks the server for that skill's details screen. */
public record OpenSkillDetailsMessage(
        BlockPos flagPos,
        UUID villager,
        JobID job,
        boolean locked,
        /** Opened from the flag: the details offer the flag's Block of Progress for Unlock. */
        boolean fromFlag
) {

    public static void encode(
            OpenSkillDetailsMessage msg,
            FriendlyByteBuf buffer
    ) {
        buffer.writeBlockPos(msg.flagPos);
        buffer.writeUUID(msg.villager);
        Jobs.writeIdToNetwork(buffer, msg.job);
        buffer.writeBoolean(msg.locked);
        buffer.writeBoolean(msg.fromFlag);
    }

    public static OpenSkillDetailsMessage decode(FriendlyByteBuf buffer) {
        return new OpenSkillDetailsMessage(
                buffer.readBlockPos(), buffer.readUUID(), Jobs.getIdFromNetwork(buffer), buffer.readBoolean(), buffer.readBoolean()
        );
    }

    public void handle(
            Channel.Context ctx
    ) {
        ctx.enqueueWork(() -> {
            ServerPlayer sender = (ServerPlayer) ctx.sender();
            Optional<TownFlagBlockEntity> flag = sender.getLevel().getBlockEntity(flagPos, TilesInit.TOWN_FLAG.get());
            if (flag.isEmpty()) {
                QT.GUI_LOGGER.error("No flag at position {}. Skill details not shown.", flagPos);
                return;
            }
            TownVillagerUIs.showSkillDetailsUI(
                    sender, flag.get(), flag.get().getVillagerHandle().entities(), job, villager, locked,
                    fromFlag ? flag.get().getBopCount() : 0
            );
        });
    }
}
