package ca.bradj.questown.commands;

import ca.bradj.questown.QT;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;

// Dev-only: register a door (a room) for a town flag by block position, so a held
// run can be driven from the console without a wand right-click. It walks the
// same path a wand click on a door takes (RoomsHolder.registerDoor), which queues
// the MultiLevelRoomDetector scan that detects the room and completes its recipe
// quest. Like the wand's DoorHandler it takes any door (no radius gate). No
// permission gate, so it runs from the console (FIFO).
public class RegisterRoomCommand {
    public static void register(CommandDispatcher<CommandSourceStack> src) {
        RequiredArgumentBuilder<CommandSourceStack, Coordinates> posArg = Commands.argument(
                "pos",
                BlockPosArgument.blockPos()
        );

        // @formatter:off
        src.register(
            Commands.literal("_qtdev").then(
                Commands.literal("registerroom")
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
        BlockState state = source.getLevel().getBlockState(target);
        if (!(state.getBlock() instanceof DoorBlock)) {
            QT.FLAG_LOGGER.warn("[registerroom] {} is not a door", target);
            return 1;
        }
        // Any flag at the door's height can claim it (the wand has no radius
        // gate on door registration); prefer the closest one if there are two.
        TownFlagBlockEntity best = null;
        int bestDist = Integer.MAX_VALUE;
        for (TownFlagBlockEntity flag : ca.bradj.questown.town.entity.TownFlags.all()) {
            BlockPos fp = flag.getBlockPos();
            if (fp == null || fp.getY() != target.getY()) {
                continue;
            }
            int d = Math.abs(target.getX() - fp.getX()) + Math.abs(target.getZ() - fp.getZ());
            if (d < bestDist) {
                bestDist = d;
                best = flag;
            }
        }
        if (best == null) {
            QT.FLAG_LOGGER.warn("[registerroom] no flag at the door's height for {}", target);
            return 1;
        }
        best.getRoomHandle().registerDoor(target);
        QT.FLAG_LOGGER.info(
                "[registerroom] registered door {} for flag {} at {}",
                target, best.getUUID(), best.getBlockPos()
        );
        return 0;
    }
}
