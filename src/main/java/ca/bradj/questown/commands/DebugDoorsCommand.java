package ca.bradj.questown.commands;

import ca.bradj.questown.blocks.TownFlagBlock;
import ca.bradj.questown.core.init.items.ItemsInit;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

public class DebugDoorsCommand {
     public static void register(CommandDispatcher<CommandSourceStack> src) {
        RequiredArgumentBuilder<CommandSourceStack, Coordinates> posArg = Commands.argument(
                "pos",
                BlockPosArgument.blockPos()
        );

        LiteralArgumentBuilder<CommandSourceStack> subCmd = Commands.literal("debug");
        LiteralArgumentBuilder<CommandSourceStack> subSubCmd = Commands.literal("doors_get_item");

        // @formatter:off
        src.register(
            Commands.literal("qt").then(
                subCmd.then(
                    subSubCmd
                        .requires(AddExperienceCommand::isCreative)
                        .then(posArg
                        .executes(css -> giveDebug(
                            css.getSource(),
                            BlockPosArgument.getLoadedBlockPos(css, "pos")
                        )))
                )
            )
        );
        // @formatter:on
    }

    private static int giveDebug(
            CommandSourceStack source,
            BlockPos target
    ) {
        @Nullable TownFlagBlockEntity tfbe = QTCommands.getFlagOrBroadcast(source, target);
        if (tfbe == null) {
            return -1;
        }

        ItemStack debugItem = ItemsInit.TOWN_DOOR_TESTER.get()
                                                              .getDefaultInstance();
        TownFlagBlock.StoreParentOnNBT(
                debugItem,
                target
        );
        try {
            source.getPlayerOrException().getInventory().add(debugItem);
        } catch (CommandSyntaxException ex) {
            throw new RuntimeException(ex);
        }
        return 0;
    }
}
