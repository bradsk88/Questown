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

// Dev-only: build a room around a door by block position, the way the helper chicken does in its
// room-build beat, but driven from the console (FIFO). A room quest needs its own room, and the
// only hand that lays a room is the chicken's beat, which a held run with no live player cannot
// trigger; this is the agent's standing-in for that beat. It delegates the whole action to the flag
// (TownFlagBlockEntity.buildRoom), which registers the door and lays the same shell the chicken's
// build goal lays (ChickenRoomShellPlan), so the agent does not hand-roll a plus-shaped room that
// RoomRecipes' wall walk rejects. No permission gate, so it runs from the console. Registered exactly
// like FlagPlaceBoardCommand (same _qtdev parent, same BlockPos arg).
public class FlagBuildRoomCommand {
    public static void register(CommandDispatcher<CommandSourceStack> src) {
        RequiredArgumentBuilder<CommandSourceStack, Coordinates> posArg = Commands.argument(
                "pos",
                BlockPosArgument.blockPos()
        );

        // @formatter:off
        src.register(
            Commands.literal("_qtdev").then(
                Commands.literal("build_room")
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
            QT.FLAG_LOGGER.warn("[build_room] no flag to build the room for");
            return 1;
        }
        flag.buildRoom(pos);
        QT.FLAG_LOGGER.info("[build_room] built a room around door {} for flag {}",
                pos, flag.getBlockPos());
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
