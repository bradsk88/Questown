package ca.bradj.questown.commands;

import ca.bradj.questown.QT;
import ca.bradj.questown.integration.minecraft.MCTownState;
import ca.bradj.questown.town.WarpDebugLog;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;

public class TimeWarpCommand {
    public static void register(CommandDispatcher<CommandSourceStack> src) {
        RequiredArgumentBuilder<CommandSourceStack, Coordinates> posArg = Commands.argument(
                "pos",
                BlockPosArgument.blockPos()
        );
        RequiredArgumentBuilder<CommandSourceStack, Integer> amtArg = Commands.argument(
                "ticks",
                IntegerArgumentType.integer()
        );

        // @formatter:off
        src.register(
            Commands.literal("qt").then(
                Commands.literal("debug").then(
                    Commands.literal("warp")
                        .requires(AddExperienceCommand::isCreative)
                        .then(posArg
                        .then(amtArg
                        .executes(css -> run(
                            css.getSource(),
                            BlockPosArgument.getLoadedBlockPos(css, "pos"),
                            IntegerArgumentType.getInteger(css, "ticks"),
                            false
                        ))
                        .then(Commands.literal("verbose")
                        .executes(css -> run(
                            css.getSource(),
                            BlockPosArgument.getLoadedBlockPos(css, "pos"),
                            IntegerArgumentType.getInteger(css, "ticks"),
                            true
                        )))
                    ))
                )
            )
        );
        // @formatter:on
    }

    private static int run(
            CommandSourceStack source,
            BlockPos target,
            Integer ticks,
            boolean verbose
    ) {
        BlockEntity e = source.getLevel().getBlockEntity(target);
        if (!(e instanceof TownFlagBlockEntity tfbe)) {
            return -1;
        }

        LogDataCommand.run(source, target, false);

        WarpDebugLog debugLog = verbose ? beginVerboseLogging(tfbe) : null;

        MCTownState afterState = tfbe.warpTime(ticks);

        if (debugLog != null) {
            endVerboseLogging(tfbe, debugLog, afterState);
        }

        return 0;
    }

    private static WarpDebugLog beginVerboseLogging(TownFlagBlockEntity tfbe) {
        tfbe.toggleDebugLog(DebugLogArgument.TIME_WARP);
        tfbe.toggleDebugLog(DebugLogArgument.TIME_WARP_DETAIL);
        tfbe.toggleDebugLog(DebugLogArgument.TIME_WARP_ITEMS);
        QT.FLAG_LOGGER.info("Verbose warp logging enabled");

        WarpDebugLog debugLog = WarpDebugLog.start();
        MCTownState beforeState = tfbe.captureCurrentState();
        if (beforeState != null) {
            debugLog.captureBeforeState(beforeState);
            QT.FLAG_LOGGER.info("=== BEFORE WARP ===");
            QT.FLAG_LOGGER.info("Containers:\n{}", WarpDebugLog.formatAllContainers(beforeState.containers));
        }
        return debugLog;
    }

    private static void endVerboseLogging(
            TownFlagBlockEntity tfbe,
            WarpDebugLog debugLog,
            @Nullable MCTownState afterState
    ) {
        QT.FLAG_LOGGER.info("=== WARP EVENTS ===");
        QT.FLAG_LOGGER.info("{}", debugLog.formatEvents());

        if (afterState != null) {
            QT.FLAG_LOGGER.info("=== AFTER WARP ===");
            QT.FLAG_LOGGER.info("Containers:\n{}", WarpDebugLog.formatAllContainers(afterState.containers));
            QT.FLAG_LOGGER.info("=== CHANGES ===");
            QT.FLAG_LOGGER.info("{}", debugLog.generateComparison(afterState));
        }

        WarpDebugLog.end();

        tfbe.toggleDebugLog(DebugLogArgument.TIME_WARP);
        tfbe.toggleDebugLog(DebugLogArgument.TIME_WARP_DETAIL);
        tfbe.toggleDebugLog(DebugLogArgument.TIME_WARP_ITEMS);
        QT.FLAG_LOGGER.info("Verbose warp logging disabled");
    }
}
