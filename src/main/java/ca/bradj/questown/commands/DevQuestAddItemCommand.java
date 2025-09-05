package ca.bradj.questown.commands;

import ca.bradj.questown.town.TownFlagBlockEntity;
import ca.bradj.questown.town.rewards.AddItemQuestReward;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;

public class DevQuestAddItemCommand {
     public static void register(CommandDispatcher<CommandSourceStack> src) {
        RequiredArgumentBuilder<CommandSourceStack, Coordinates> posArg = Commands.argument(
                "pos",
                BlockPosArgument.blockPos()
        );

        LiteralArgumentBuilder<CommandSourceStack> subCmd = Commands.literal("quests");
        LiteralArgumentBuilder<CommandSourceStack> subSubCmd = Commands.literal("add_item_quest");

        if (!System.getenv().containsKey("ENABLE_DEV_COMMANDS")) {
            return;
        }

        // @formatter:off
        src.register(
            Commands.literal("_qtdev").then(
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

        tfbe.addImmediateReward(
                new AddItemQuestReward(tfbe, ForgeRegistries.ITEMS.getKey(Items.APPLE), 10)
        );

        return 0;
    }
}
