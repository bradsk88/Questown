package ca.bradj.questown.commands;

import ca.bradj.questown.town.TownFlagBlockEntity;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;

import javax.annotation.Nullable;

public class FreezeCommand {
    public static void register(CommandDispatcher<CommandSourceStack> src) {
        RequiredArgumentBuilder<CommandSourceStack, Coordinates> posArg = Commands.argument(
                "pos",
                BlockPosArgument.blockPos()
        );
        RequiredArgumentBuilder<CommandSourceStack, Integer> amtArg = Commands.argument(
                "ticks",
                IntegerArgumentType.integer()
        );

        LiteralArgumentBuilder<CommandSourceStack> subCmd = Commands.literal("villagers");
        LiteralArgumentBuilder<CommandSourceStack> subSubCmd = Commands.literal("freeze_all");

        // @formatter:off
        src.register(
            Commands.literal("qt").then(
                subCmd.then(
                    subSubCmd
                        .requires(AddExperienceCommand::isCreative)
                        .then(posArg
                        .then(amtArg
                        .executes(css -> run(
                            css.getSource(),
                            BlockPosArgument.getLoadedBlockPos(css, "pos"),
                                IntegerArgumentType.getInteger(css, "ticks")
                        ))))
                )
            )
        );
        // @formatter:on
    }

    private static int run(
            CommandSourceStack source,
            BlockPos target,
            Integer ticks
    ) {
        @Nullable TownFlagBlockEntity tfbe = QTCommands.getFlagOrBroadcast(source, target);
        if (tfbe == null) {
            return -1;
        }

        tfbe.getVillagerHandle().freezeVillagers(ticks);
        return 0;
    }
}
