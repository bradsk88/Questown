package ca.bradj.questown.commands;

import ca.bradj.questown.QT;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import ca.bradj.questown.mobs.helperchicken.ChickenArcConditions;
import ca.bradj.roomrecipes.core.space.InclusiveSpace;
import ca.bradj.roomrecipes.core.space.Position;
import ca.bradj.roomrecipes.logic.InclusiveSpaces;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

// Dev-only: put the nearest player inside a registered room's interior, the way the
// player would walk in, but driven from the console (FIFO). The chicken's chest beat
// (HelperChickenSunsetChestGoal) drops its chest only once playerInStoreShell is true
// -- the player is inside an interior cell of a registered room -- so a held run that
// cannot move the player by hand needs this to satisfy the witness gate. Vanilla
// `tp <x> <y> <z>` teleports the command executor (the console), which has no player,
// so it no-ops; this teleports the actual player the witness gate checks
// (ChickenArcConditions.findNearestPlayerForFlag).
//
// It reuses the same interior-cell logic the chicken uses to pick its chest target
// (InclusiveSpaces ... INTERIOR_ONLY), then lands the player on a passable interior
// cell. No permission gate, so it runs from the console (FIFO).
public class FlagPlacePlayerCommand {
    public static void register(CommandDispatcher<CommandSourceStack> src) {
        // @formatter:off
        src.register(
            Commands.literal("_qtdev").then(
                Commands.literal("place_player")
                    .executes(css -> run(css.getSource()))
            )
        );
        // @formatter:on
    }

    private static int run(CommandSourceStack source) {
        TownFlagBlockEntity flag = firstFlag();
        if (flag == null) {
            QT.FLAG_LOGGER.warn("[place_player] no flag to place the player for");
            return 1;
        }
        Player player = ChickenArcConditions.findNearestPlayerForFlag(flag);
        if (player == null) {
            QT.FLAG_LOGGER.warn("[place_player] no player near flag {}", flag.getUUID());
            return 1;
        }
        BlockPos interior = interiorCell(flag);
        if (interior == null) {
            QT.FLAG_LOGGER.warn(
                    "[place_player] no interior cell in any registered room for flag {}",
                    flag.getUUID()
            );
            return 1;
        }
        player.teleportTo(
                (double) interior.getX() + 0.5D,
                (double) interior.getY() + 0.5D,
                (double) interior.getZ() + 0.5D
        );
        QT.FLAG_LOGGER.info(
                "[place_player] teleported {} to {} in a registered room",
                player.getName().getString(),
                interior
        );
        return 0;
    }

    /**
     * A passable interior cell of the flag's largest registered room, at the room's
     * floor level. Mirrors the chicken's own chest-target search (largestRoom +
     * INTERIOR_ONLY positions), so the player lands in the same room the chicken is
     * working. Returns null when no registered room has a passable interior cell.
     */
    private static @Nullable BlockPos interiorCell(TownFlagBlockEntity flag) {
        Collection<MCRoom> rooms = flag.getRoomHandle().getRegisteredRooms().getAllRooms();
        if (rooms.isEmpty()) {
            return null;
        }
        MCRoom shell = largestRoom(rooms);
        for (InclusiveSpace space : shell.getSpaces()) {
            for (Position p : InclusiveSpaces.getPositions(
                    space, InclusiveSpaces.PositionType.INTERIOR_ONLY)) {
                // A contained block sits at the room's floor level or one above; try
                // both, and skip a cell that is already walled (a door, terrain).
                for (int y : new int[]{shell.yCoord, shell.yCoord + 1}) {
                    BlockPos c = new BlockPos(p.x, y, p.z);
                    BlockState state = flag.getServerLevel().getBlockState(c);
                    if (state.isAir() || state.getMaterial().isReplaceable()) {
                        return c;
                    }
                }
            }
        }
        return null;
    }

    private static MCRoom largestRoom(Collection<MCRoom> rooms) {
        MCRoom best = null;
        double bestArea = -1;
        for (MCRoom r : rooms) {
            double area = InclusiveSpaces.calculateArea(r.getSpaces());
            if (area > bestArea) {
                bestArea = area;
                best = r;
            }
        }
        return best;
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
