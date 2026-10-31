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

// Dev-only: place a full (both-halves) door, the way a player's door item would, but
// driven from the console (FIFO). Vanilla setblock easily places only the LOWER half of
// a door, leaving a one-block gap that then gets roofed over and breaks the door; this
// delegates to TownFlagBlockEntity.placeDoor, which places both halves. No permission
// gate, so it runs from the console. Registered exactly like FlagPlaceBoardCommand.
// Registering the door for room detection is a separate step (_qtdev registerroom),
// mirroring the player flow.
//
// Two forms. `_qtdev place_door <x> <y> <z>` places at the given block, the way a player
// would. Bare `_qtdev place_door` (no coordinate) places the door at the chicken's door
// target - the door offset from the flag, the flag's structure rotation applied - which
// is the same position the door beat's condition checks. The chicken's cue "pecks at a
// gap" is a holdover from its home empty_town structure, which really has a gap there; a
// hand-placed flag has no gap, so an agent that can not see the peck has no coordinate to
// hunt. The bare form hands over that target for the agent, so it places the door where
// the beat wants it without needing to find "the gap". The beat only needs a DoorBlock at
// the target, so this advances it for either flag origin.
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

    /**
     * The no-coordinate form: place the door at the chicken's door target, the position
     * the door beat's condition checks, so an agent that can not see the chicken's peck
     * still lands the door where the beat wants it.
     */
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
