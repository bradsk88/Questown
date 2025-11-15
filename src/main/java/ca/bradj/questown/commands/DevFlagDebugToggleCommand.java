package ca.bradj.questown.commands;

import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.entity.BlockEntity;

public class DevFlagDebugToggleCommand {
    public static void register(
            CommandDispatcher<CommandSourceStack> src,
            CommandBuildContext ctx
    ) {
        if (!System.getenv().containsKey("ENABLE_DEV_COMMANDS")) {
            return;
        }

        RequiredArgumentBuilder<CommandSourceStack, Coordinates> posArg = Commands.argument(
                "pos",
                BlockPosArgument.blockPos()
        );
        RequiredArgumentBuilder<CommandSourceStack, String> logIdArg = Commands.argument(
                "log_id",
                new DebugLogArgument(ctx)
        );

        LiteralArgumentBuilder<CommandSourceStack> subCmd = Commands.literal("debug");
        LiteralArgumentBuilder<CommandSourceStack> subSubCmd = Commands.literal("toggle_log");

        // @formatter:off
        src.register(
            Commands.literal("_qtdev").then(
                subCmd
                    .then(posArg
                    .then(subSubCmd
                    .then(logIdArg
                    .executes(css -> toggleLog(
                        css.getSource(),
                        BlockPosArgument.getLoadedBlockPos(css, "pos"),
                        DebugLogArgument.getLog(css, "log_id")
                    ))))
                )
            )
        );
        // @formatter:on
    }

    private static int toggleLog(
            CommandSourceStack source,
            BlockPos target,
            String logId
    ) {
        BlockEntity e = source.getLevel().getBlockEntity(target);
        if (!(e instanceof TownFlagBlockEntity tf)) {
            return 0;
        }

        tf.toggleDebugLog(logId);

        return 0;
    }
}
