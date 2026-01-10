package ca.bradj.questown.commands;

import ca.bradj.questown.QT;
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
            // TODO: Better error handling?
            return -1;
        }

        // Log data to help with debugging time warps
        LogDataCommand.run(source, target, false);

        if (verbose) {
            // Enable detailed warp logging for this warp
            tfbe.toggleDebugLog(DebugLogArgument.TIME_WARP);
            tfbe.toggleDebugLog(DebugLogArgument.TIME_WARP_DETAIL);
            tfbe.toggleDebugLog(DebugLogArgument.TIME_WARP_ITEMS);
            QT.FLAG_LOGGER.info("Verbose warp logging enabled");
        }

        tfbe.warpTime(ticks);

        if (verbose) {
            // Disable logging after warp completes
            tfbe.toggleDebugLog(DebugLogArgument.TIME_WARP);
            tfbe.toggleDebugLog(DebugLogArgument.TIME_WARP_DETAIL);
            tfbe.toggleDebugLog(DebugLogArgument.TIME_WARP_ITEMS);
            QT.FLAG_LOGGER.info("Verbose warp logging disabled");
        }

        return 0;
    }
}
