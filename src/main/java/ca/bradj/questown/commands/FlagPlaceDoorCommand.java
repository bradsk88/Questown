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
import org.jetbrains.annotations.Nullable;

// Dev-only: place a full (both-halves) door at a block position, the way a player's
// door item would, but driven from the console (FIFO). Vanilla setblock easily places
// only the LOWER half of a door, leaving a one-block gap that then gets roofed over and
// breaks the door; this delegates to TownFlagBlockEntity.placeDoor, which places both
// halves. No permission gate, so it runs from the console. Registered exactly like
// FlagPlaceBoardCommand (same _qtdev parent, same BlockPos arg) so a held run can place
// a valid door without a player. Registering the door for room detection is a separate
// step (_qtdev registerroom), mirroring the player flow.
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
                    .then(posArg
                        .executes(css -> run(
                            css.getSource(),
                            BlockPosArgument.getLoadedBlockPos(css, "pos")
                        )))
            )
        );
        // @formatter:on
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
