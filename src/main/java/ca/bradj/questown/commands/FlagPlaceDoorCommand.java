package ca.bradj.questown.commands;

import ca.bradj.questown.QT;
import ca.bradj.questown.mobs.helperchicken.ChickenBeatState;
import ca.bradj.questown.mobs.helperchicken.HelperChickenBeatOffsets;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

// Dev-only: place a whole door (both halves). `_qtdev place_door <x> <y> <z>` places it there; bare
// `_qtdev place_door` places it where the chicken's door lesson wants it. Register it with
// `_qtdev registerroom`.
public class FlagPlaceDoorCommand {
    public static void register(CommandDispatcher<CommandSourceStack> src) {
        RequiredArgumentBuilder<CommandSourceStack, Coordinates> posArg = Commands.argument(
                "pos",
                BlockPosArgument.blockPos()
        );

        // @formatter:off
        src.register(
            Commands.literal("_qtdev").then(
                Commands.literal("place_door")
                    .executes(css -> runAtDoorTarget(css.getSource()))
                    .then(posArg
                        .executes(css -> run(
                            css.getSource(),
                            BlockPosArgument.getLoadedBlockPos(css, "pos")
                        )))
            )
        );
        // @formatter:on
    }

    private static int runAtDoorTarget(CommandSourceStack source) {
        TownFlagBlockEntity flag = firstFlag();
        if (flag == null) {
            QT.FLAG_LOGGER.warn("[place_door] no flag to place the door for");
            return 1;
        }
        BlockPos target = HelperChickenBeatOffsets.resolveTarget(
                ChickenBeatState.WAITING_FOR_DOOR,
                flag.getBlockPos(),
                flag.getChickenStructureRotation()
        );
        if (target == null) {
            QT.FLAG_LOGGER.warn("[place_door] could not resolve the door target for flag {}",
                    flag.getBlockPos());
            return 1;
        }
        flag.placeDoor(target);
        QT.FLAG_LOGGER.info("[place_door] placed a full door at the door target {} (no coordinate given)",
                target);
        return 0;
    }

    private static int run(CommandSourceStack source, BlockPos pos) {
        TownFlagBlockEntity flag = firstFlag();
        if (flag == null) {
            QT.FLAG_LOGGER.warn("[place_door] no flag to place the door for");
            return 1;
        }
        flag.placeDoor(pos);
        QT.FLAG_LOGGER.info("[place_door] placed a full door at {}", pos);
        return 0;
    }

    private static @Nullable TownFlagBlockEntity firstFlag() {
        for (TownFlagBlockEntity f : ca.bradj.questown.town.entity.TownFlags.all()) {
            if (f.getBlockPos() != null) {
                return f;
            }
        }
        return null;
    }
}
