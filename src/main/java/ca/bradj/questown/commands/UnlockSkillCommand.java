package ca.bradj.questown.commands;

import ca.bradj.questown.QT;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.ServerJobsRegistry;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import ca.bradj.questown.town.entity.TownFlags;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;

import java.util.UUID;

// Dev-only: unlock a skill on a villager with one of the flag's Blocks of Progress, as the skills
// tab does: a thin wrapper over TownFlagBlockEntity.spendBopToUnlockSkill. The job is "root/job".
public class UnlockSkillCommand {

    public static void register(CommandDispatcher<CommandSourceStack> src) {
        // @formatter:off
        src.register(
            Commands.literal("_qtdev").then(
                Commands.literal("unlock_skill")
                    .then(Commands.argument("villager", UuidArgument.uuid())
                        .then(Commands.argument("job", StringArgumentType.string())
                            .executes(css -> run(
                                    UuidArgument.getUuid(css, "villager"),
                                    StringArgumentType.getString(css, "job")))))
            )
        );
        // @formatter:on
    }

    private static int run(UUID villagerUUID, String job) {
        JobID id = ServerJobsRegistry.parseStringValue(job);
        for (TownFlagBlockEntity flag : TownFlags.all()) {
            if (flag.getBlockPos() == null) {
                continue;
            }
            TownFlagBlockEntity.SkillUnlockResult r = flag.spendBopToUnlockSkill(villagerUUID, id);
            QT.FLAG_LOGGER.info("[unlock_skill] {} for villager {}: {}", id.toNiceString(), villagerUUID, r);
            return r == TownFlagBlockEntity.SkillUnlockResult.UNLOCKED ? 0 : 1;
        }
        QT.FLAG_LOGGER.warn("[unlock_skill] no flag to unlock a skill on");
        return 1;
    }
}
