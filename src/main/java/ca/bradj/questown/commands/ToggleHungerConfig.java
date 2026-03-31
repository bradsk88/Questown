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

public class ToggleHungerConfig {
    public static void register(CommandDispatcher<CommandSourceStack> src) {
        RequiredArgumentBuilder<CommandSourceStack, Coordinates> posArg = Commands.argument(
                "pos",
                BlockPosArgument.blockPos()
        );

        LiteralArgumentBuilder<CommandSourceStack> subCmd = Commands.literal("config");
        LiteralArgumentBuilder<CommandSourceStack> subSubCmd = Commands.literal("toggle_hunger");

        // @formatter:off
        src.register(
            Commands.literal("qt").then(
                subCmd
                    .requires(AddExperienceCommand::isCreative)
                    .then(posArg
                        .then(
                            subSubCmd
                            .executes(css -> toggleHunger(
                                css.getSource(),
                                BlockPosArgument.getLoadedBlockPos(css, "pos")
                            )))
                )
            )
        );
        // @formatter:on
    }

    private static int toggleHunger(
            CommandSourceStack source,
            BlockPos target
    ) {
        BlockEntity e = source.getLevel().getBlockEntity(target);
        if (!(e instanceof TownFlagBlockEntity tf)) {
            return 0;
        }

        tf.getVillagerHandle().toggleHunger();

        source.getLevel().removeBlockEntity(target);

        source.getLevel().removeBlock(target, true);

        return 0;
    }
}
