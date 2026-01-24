package ca.bradj.questown.commands;

import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;

public class DevFlagDebugTurboCommand {
    public static void register(
            CommandDispatcher<CommandSourceStack> src
    ) {
        if (!System.getenv().containsKey("ENABLE_DEV_COMMANDS")) {
            return;
        }

        RequiredArgumentBuilder<CommandSourceStack, Coordinates> posArg = Commands.argument(
                "pos",
                BlockPosArgument.blockPos()
        );

        LiteralArgumentBuilder<CommandSourceStack> subCmd = Commands.literal("debug");
        LiteralArgumentBuilder<CommandSourceStack> subSubCmd = Commands.literal("turbo_mode");

        // @formatter:off
        src.register(
            Commands.literal("_qtdev").then(
                subCmd
                    .then(posArg
                    .then(subSubCmd
                    .executes(css -> toggleLog(
                        css.getSource(),
                        BlockPosArgument.getLoadedBlockPos(css, "pos")
                    )))
                )
            )
        );
        // @formatter:on
    }

    private static int toggleLog(
            CommandSourceStack source,
            BlockPos target
    ) {
        BlockEntity e = source.getLevel().getBlockEntity(target);
        if (!(e instanceof TownFlagBlockEntity tf)) {
            return 0;
        }

        DebugLogArgument.debugLogIds.forEach(l -> tf.setDebugLog(l, true));

        return 0;
    }
}
