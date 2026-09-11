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

// Dev-only: place a welcome-mat block for a town flag, the way a player's
// craft-then-place would, but driven from the console (FIFO). The dev is an
// observer, not the one holding a pressure plate, so the flag entity places and
// registers the welcome-mat block itself (TownFlagBlockEntity.placeWelcomeMat).
// No permission gate, so it runs from the console. Registered exactly like the
// working RegisterRoomCommand (same qt parent, same BlockPos arg) so a held run
// can drive the welcome-mat quest without a player.
//
// Two forms. `_qtdev place_mat <x> <y> <z>` places at the given block, the way a
// player would. Bare `_qtdev place_mat` (no coordinate) places the mat a block east
// of the flag - the spot the non-agent VillageGrowthExecutor path uses - which a
// hand-placed flag always has and which never lands on the door (a mat on the door
// replaces it and un-registers the room the agent built). The chicken's cue "put it
// on the gate" is a holdover from its home empty_town structure, which really has a
// gate there; a hand-placed flag has no gate, so the welcome mat has no fixed target
// - it can go anywhere - and the bare form hands over a safe spot for the agent, so
// it does not invent one the way it would if the command only took a coordinate
// (the way the bare place_door hands over the door target).
public class FlagCraftCommand {
    public static void register(CommandDispatcher<CommandSourceStack> src) {
        RequiredArgumentBuilder<CommandSourceStack, Coordinates> posArg = Commands.argument(
                "pos",
                BlockPosArgument.blockPos()
        );

        // @formatter:off
        src.register(
            Commands.literal("_qtdev").then(
                Commands.literal("place_mat")
                    .executes(css -> runAtFlagBeside(css.getSource()))
                    .then(posArg
                        .executes(css -> run(
                            css.getSource(),
                            BlockPosArgument.getLoadedBlockPos(css, "pos")
                        )))
            )
        );
        // @formatter:on
    }

    /**
     * The no-coordinate form: place the welcome mat a block east of the flag, the
     * spot the non-agent VillageGrowthExecutor path uses. A hand-placed flag has no
     * gate, so the mat (which the chicken narrates "on the gate") has no fixed target;
     * this hands over a safe spot that never lands on the door, so the agent does not
     * invent one (the way the bare place_door hands over the door target).
     */
    private static int runAtFlagBeside(CommandSourceStack source) {
        TownFlagBlockEntity flag = firstFlag();
        if (flag == null) {
            QT.FLAG_LOGGER.warn("[place_mat] no flag to place the welcome mat for");
            return 1;
        }
        BlockPos target = flag.getBlockPos().east();
        flag.placeWelcomeMat(target);
        QT.FLAG_LOGGER.info("[place_mat] placed a welcome mat at {} (no coordinate given, beside the flag)",
                target);
        return 0;
    }

    private static int run(CommandSourceStack source, BlockPos pos) {
        TownFlagBlockEntity flag = firstFlag();
        if (flag == null) {
            QT.FLAG_LOGGER.warn("[place_mat] no flag to place the welcome mat for");
            return 1;
        }
        flag.placeWelcomeMat(pos);
        QT.FLAG_LOGGER.info("[place_mat] placed a welcome mat at {}", pos);
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
