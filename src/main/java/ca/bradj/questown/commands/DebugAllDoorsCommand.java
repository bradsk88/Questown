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

import javax.annotation.Nullable;

public class DebugAllDoorsCommand {
    public static void register(CommandDispatcher<CommandSourceStack> src) {
        RequiredArgumentBuilder<CommandSourceStack, Coordinates> posArg = Commands.argument(
                "pos",
                BlockPosArgument.blockPos()
        );

        LiteralArgumentBuilder<CommandSourceStack> subCmd = Commands.literal("debug");
        LiteralArgumentBuilder<CommandSourceStack> subSubCmd = Commands.literal("doors_all");

        // @formatter:off
        src.register(
            Commands.literal("qt").then(
                subCmd.then(
                    subSubCmd
                        .requires(AddExperienceCommand::isCreative)
                        .then(posArg
                        .executes(css -> startDebug(
                            css.getSource(),
                            BlockPosArgument.getLoadedBlockPos(css, "pos")
                        )))
                )
            )
        );
        // @formatter:on
    }

    private static int startDebug(
            CommandSourceStack source,
            BlockPos target
    ) {
        @Nullable TownFlagBlockEntity tfbe = QTCommands.getFlagOrBroadcast(source, target);
        if (tfbe == null) {
            return -1;
        }

        tfbe.startDebugTask(tfbe.getRoomHandle().getDebugTaskForAllDoors());
        return 0;
    }
}
