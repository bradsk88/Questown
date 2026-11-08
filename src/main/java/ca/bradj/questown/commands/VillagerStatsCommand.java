package ca.bradj.questown.commands;

import ca.bradj.questown.QT;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;

// Dev-only, read-only: every villager's stats (level, xp, fullness, mood, damage, mood effects).
public class VillagerStatsCommand {
    public static void register(CommandDispatcher<CommandSourceStack> src) {
        RequiredArgumentBuilder<CommandSourceStack, Coordinates> posArg = Commands.argument(
                "flag",
                BlockPosArgument.blockPos()
        );

        // @formatter:off
        src.register(
            Commands.literal("_qtdev").then(
                Commands.literal("villager_stats")
                    .then(posArg
                        .executes(css -> run(BlockPosArgument.getLoadedBlockPos(css, "flag"), css.getSource())))
            )
        );
        // @formatter:on
    }

    private static int run(BlockPos flagPos, CommandSourceStack source) {
        if (!(source.getLevel().getBlockEntity(flagPos) instanceof TownFlagBlockEntity flag)) {
            QT.FLAG_LOGGER.warn("[villager_stats] no town flag at {}", flagPos);
            return 1;
        }
        for (LivingEntity e : flag.getVillagerHandle().entities()) {
            QT.FLAG_LOGGER.info(
                    "[villager_stats] {} ({}): {}",
                    e.getName().getString(), e.getUUID(), flag.getVillagerHandle().getStats(e.getUUID()).summary()
            );
        }
        return 0;
    }
}
