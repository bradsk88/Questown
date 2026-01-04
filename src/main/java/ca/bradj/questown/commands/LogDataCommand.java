package ca.bradj.questown.commands;

import ca.bradj.questown.QT;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;

public class LogDataCommand {
    public static void register(CommandDispatcher<CommandSourceStack> src) {
        RequiredArgumentBuilder<CommandSourceStack, Coordinates> posArg = Commands.argument(
                "pos",
                BlockPosArgument.blockPos()
        );

        LiteralArgumentBuilder<CommandSourceStack> subCmd = Commands.literal("debug");
        LiteralArgumentBuilder<CommandSourceStack> subSubCmd = Commands.literal("log");

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

        CompoundTag tTag = new CompoundTag();
        tfbe.writeTownData(tTag);

        TownFlagBlockEntity.logStoredData(tfbe, tTag);

        QT.FLAG_LOGGER.info("NeededRooms: {}", tfbe.getEconomicsHandle().getAggregatedRooms());
        return 0;
    }
}
