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

// Dev-only: put the nearest player on a passable interior cell of a registered room, the
// witness the chicken's chest beat waits for (ChickenArcConditions.findNearestPlayerForFlag).
// A console `tp` moves the command source, which is not a player.
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
                    if (state.isAir() || ca.bradj.questown.mc.Compat.canBeReplaced(state)) {
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
