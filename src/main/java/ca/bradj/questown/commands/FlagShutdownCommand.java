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

/**
 * {@code /qt flag shutdown <pos>} begins the town-shutdown relocation ritual on the flag at
 * {@code pos}; {@code /qt flag shutdown <pos> cancel} aborts an in-progress ritual (ADR-0009, #199).
 *
 * <p>This is the concrete trigger for the ritual. The polished in-game entry point — a "Begin
 * moving this town" action on the flag menu — is GUI work (a documented server-autotest blind spot)
 * and wraps these same {@link TownFlagBlockEntity#beginTownShutdown()} /
 * {@link TownFlagBlockEntity#cancelTownShutdown()} entry points.
 */
public class FlagShutdownCommand {
    public static void register(CommandDispatcher<CommandSourceStack> src) {
        RequiredArgumentBuilder<CommandSourceStack, Coordinates> posArg = Commands.argument(
                "pos",
                BlockPosArgument.blockPos()
        );

        LiteralArgumentBuilder<CommandSourceStack> subCmd = Commands.literal("flag");
        LiteralArgumentBuilder<CommandSourceStack> subSubCmd = Commands.literal("shutdown");

        // @formatter:off
        src.register(
            Commands.literal("qt").then(
                subCmd.then(posArg
                    .then(subSubCmd
                        .executes(css -> begin(
                                css.getSource(),
                                BlockPosArgument.getLoadedBlockPos(css, "pos")
                        ))
                        .then(Commands.literal("cancel").executes(css -> cancel(
                                css.getSource(),
                                BlockPosArgument.getLoadedBlockPos(css, "pos")
                        )))
                    )
                )
            )
        );
        // @formatter:on
    }

    private static int begin(
            CommandSourceStack source,
            BlockPos target
    ) {
        TownFlagBlockEntity tf = QTCommands.getFlagOrBroadcast(source, target);
        if (tf == null) {
            return 0;
        }
        boolean started = tf.beginTownShutdown();
        source.sendSuccess(
                net.minecraft.network.chat.Component.literal(
                        started ? "Town shutdown started" : "Flag is not eligible for shutdown"
                ),
                false
        );
        return started ? 1 : 0;
    }

    private static int cancel(
            CommandSourceStack source,
            BlockPos target
    ) {
        TownFlagBlockEntity tf = QTCommands.getFlagOrBroadcast(source, target);
        if (tf == null) {
            return 0;
        }
        boolean cancelled = tf.cancelTownShutdown();
        source.sendSuccess(
                net.minecraft.network.chat.Component.literal(
                        cancelled ? "Town shutdown cancelled" : "No shutdown in progress"
                ),
                false
        );
        return cancelled ? 1 : 0;
    }
}
