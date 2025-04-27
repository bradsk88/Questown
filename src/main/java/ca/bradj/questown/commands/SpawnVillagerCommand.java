package ca.bradj.questown.commands;

import ca.bradj.questown.town.TownFlagBlockEntity;
import ca.bradj.questown.town.rewards.SpawnVisitorReward;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;

import javax.annotation.Nullable;

public class SpawnVillagerCommand {
     public static void register(CommandDispatcher<CommandSourceStack> src) {
        RequiredArgumentBuilder<CommandSourceStack, Coordinates> posArg = Commands.argument(
                "pos",
                BlockPosArgument.blockPos()
        );

        LiteralArgumentBuilder<CommandSourceStack> subCmd = Commands.literal("villagers");
        LiteralArgumentBuilder<CommandSourceStack> subSubCmd = Commands.literal("spawn");

        // @formatter:off
        src.register(
            Commands.literal("qt").then(
                subCmd.then(
                    subSubCmd
                        .requires(AddExperienceCommand::isCreative)
                        .then(posArg
                        .executes(css -> run(
                            css.getSource(),
                            BlockPosArgument.getLoadedBlockPos(css, "pos")
                        )))
                )
            )
        );
        // @formatter:on
    }

    private static int run(
            CommandSourceStack source,
            BlockPos target
    ) {
        @Nullable TownFlagBlockEntity tfbe = QTCommands.getFlagOrBroadcast(source, target);
        if (tfbe == null) {
            return -1;
        }

        tfbe.addImmediateReward(new SpawnVisitorReward(tfbe));
        return 0;
    }
}
