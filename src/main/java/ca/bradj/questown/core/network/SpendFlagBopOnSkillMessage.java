package ca.bradj.questown.core.network;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.advancements.TutorialTrigger;
import ca.bradj.questown.core.init.AdvancementsInit;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.Jobs;
import ca.bradj.questown.loader.Channel;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/**
 * Unlock a skill with one of the flag's Blocks of Progress, from the skill details opened through the
 * flag (dev playtest 2026-10-29: "they should be able to just spend directly from the flag instead
 * of having to withdraw first"). The owner call is {@link TownFlagBlockEntity#spendBopToUnlockSkill},
 * which applies the skills tab's level gate and tree membership.
 */
public record SpendFlagBopOnSkillMessage(BlockPos flagPos, UUID villagerUUID, JobID id) {

    public static void encode(
            SpendFlagBopOnSkillMessage msg,
            FriendlyByteBuf buffer
    ) {
        buffer.writeBlockPos(msg.flagPos);
        buffer.writeUUID(msg.villagerUUID);
        Jobs.writeIdToNetwork(buffer, msg.id);
    }

    public static SpendFlagBopOnSkillMessage decode(FriendlyByteBuf buffer) {
        return new SpendFlagBopOnSkillMessage(buffer.readBlockPos(), buffer.readUUID(), Jobs.getIdFromNetwork(buffer));
    }

    public void handle(
            Channel.Context ctx
    ) {
        ctx.enqueueWork(() -> {
            ServerPlayer sender = (ServerPlayer) ctx.sender();
            if (!(sender.getLevel().getBlockEntity(flagPos) instanceof TownFlagBlockEntity flag)) {
                QT.GUI_LOGGER.error("No flag at position {}. Skill not unlocked.", flagPos);
                return;
            }
            TownFlagBlockEntity.SkillUnlockResult r = flag.spendBopToUnlockSkill(villagerUUID, id);
            if (r != TownFlagBlockEntity.SkillUnlockResult.UNLOCKED) {
                QT.GUI_LOGGER.info("Flag BOP skill unlock of {} for {} refused: {}", id, villagerUUID, r);
                return;
            }
            AdvancementsInit.TUTORIAL_TRIGGER.trigger(sender, TutorialTrigger.Triggers.FirstBopSpend);
            MutableComponent msg = Compat.translatable(
                    "messages.jobs.unlocked_from_flag", sender.getName(),
                    flag.getVillagerHandle().labelFor(villagerUUID), id.toNiceString()
            );
            for (Player player : sender.getLevel().players()) {
                Compat.sendMessage((ServerPlayer) player, msg);
            }
        });
    }
}
