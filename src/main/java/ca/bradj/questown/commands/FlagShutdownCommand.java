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
 * {@code /qt flag <pos> shutdown} begins the town-shutdown relocation ritual on the flag at
 * {@code pos}; {@code /qt flag <pos> shutdown cancel} aborts an in-progress ritual (#199), and
 * {@code wake} / {@code reissue} are the dormant flag's loss-protection paths.
 *
 * <p>The admin/dev trigger for the ritual. The in-game entry point, the "Begin moving this town"
 * button on the flag menu's Settings tab
 * ({@link ca.bradj.questown.core.network.BeginTownRelocationMessage}), wraps the same
 * {@link TownFlagBlockEntity#beginTownShutdown()}; players reach cancel, wake and re-issue through
 * the flag UI too. The ritual itself is covered by the {@code flag/town_shutdown} autotest.
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
                        .then(Commands.literal("reissue").executes(css -> reissue(
                                css.getSource(),
                                BlockPosArgument.getLoadedBlockPos(css, "pos")
                        )))
                        .then(Commands.literal("wake").executes(css -> wake(
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
        source.sendSuccess(net.minecraft.network.chat.Component.literal(
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
        source.sendSuccess(net.minecraft.network.chat.Component.literal(
                        cancelled ? "Town shutdown cancelled" : "No shutdown in progress"
                ),
                false
        );
        return cancelled ? 1 : 0;
    }

    private static int reissue(
            CommandSourceStack source,
            BlockPos target
    ) {
        TownFlagBlockEntity tf = QTCommands.getFlagOrBroadcast(source, target);
        if (tf == null) {
            return 0;
        }
        boolean ok = tf.reissueDeed();
        source.sendSuccess(net.minecraft.network.chat.Component.literal(
                        ok ? "Relocation deed re-issued" : "Flag is not dormant"
                ),
                false
        );
        return ok ? 1 : 0;
    }

    private static int wake(
            CommandSourceStack source,
            BlockPos target
    ) {
        TownFlagBlockEntity tf = QTCommands.getFlagOrBroadcast(source, target);
        if (tf == null) {
            return 0;
        }
        boolean ok = tf.wakeInPlace();
        source.sendSuccess(net.minecraft.network.chat.Component.literal(
                        ok ? "Town woken in place" : "Flag is not dormant"
                ),
                false
        );
        return ok ? 1 : 0;
    }
}
