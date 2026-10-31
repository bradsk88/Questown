package ca.bradj.questown.commands;

import ca.bradj.questown.QT;
import ca.bradj.questown.town.HelperChickenSpawnController;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;

// Dev-only: force the helper chicken to spawn for a hand-placed flag, so a
// held run can drive onboarding on a flag that was not placed in an
// empty_town structure (and so never had its rotation detected). The action
// lives on the owner, HelperChickenSpawnController.forceSpawn, which clears the
// forfeit, reads the rotation once, and spawns the chicken - this command only
// finds the flag and reports the result. No permission gate, so it runs from the
// console (FIFO). Registered like the other _qtdev commands.
public class QtDevSpawnChickenCommand {
    public static void register(CommandDispatcher<CommandSourceStack> src) {
        RequiredArgumentBuilder<CommandSourceStack, Coordinates> posArg = Commands.argument(
                "pos",
                BlockPosArgument.blockPos()
        );

        // @formatter:off
        src.register(
            Commands.literal("_qtdev").then(
                Commands.literal("spawn_chicken")
                    .then(posArg
                        .executes(css -> run(
                            css.getSource(),
                            BlockPosArgument.getLoadedBlockPos(css, "pos")
                        )))
            )
        );
        // @formatter:on
    }

    private static int run(CommandSourceStack source, BlockPos target) {
        BlockEntity e = source.getLevel().getBlockEntity(target);
        if (!(e instanceof TownFlagBlockEntity flag)) {
            QT.FLAG_LOGGER.warn("[spawn_chicken] {} is not a flag", target);
            return 1;
        }
        boolean spawned = HelperChickenSpawnController.forceSpawn(flag);
        if (spawned) {
            QT.FLAG_LOGGER.info("[spawn_chicken] spawned helper chicken for flag at {}", target);
        } else {
            QT.FLAG_LOGGER.warn("[spawn_chicken] could not spawn a chicken for flag at {}", target);
        }
        return spawned ? 0 : 1;
    }
}
