package ca.bradj.questown.commands;

import ca.bradj.questown.QT;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import ca.bradj.roomrecipes.core.space.InclusiveSpace;
import ca.bradj.roomrecipes.core.space.Position;
import ca.bradj.roomrecipes.logic.InclusiveSpaces;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

// Dev-only: put the nearest player inside a registered room. A console `tp` moves the console,
// not a player.
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
        Player player = nearestPlayer(flag);
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

    /** An empty floor cell inside the flag's largest room, or null if there is none. */
    private static @Nullable BlockPos interiorCell(TownFlagBlockEntity flag) {
        Collection<MCRoom> rooms = flag.getRoomHandle().getRegisteredRooms().getAllRooms();
        if (rooms.isEmpty()) {
            return null;
        }
        MCRoom shell = largestRoom(rooms);
        for (InclusiveSpace space : shell.getSpaces()) {
            for (Position p : InclusiveSpaces.getPositions(
                    space, InclusiveSpaces.PositionType.INTERIOR_ONLY)) {
                for (int y : new int[]{shell.yCoord, shell.yCoord + 1}) {
                    BlockPos c = new BlockPos(p.x, y, p.z);
                    BlockState state = flag.getServerLevel().getBlockState(c);
                    if (state.isAir() || state.canBeReplaced()) {
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

    private static final double PLAYER_SEARCH_RADIUS = 16.0D;

    private static @Nullable Player nearestPlayer(TownFlagBlockEntity flag) {
        ServerLevel level = flag.getServerLevel();
        if (level == null) {
            return null;
        }
        BlockPos p = flag.getTownFlagBasePos();
        return level.getNearestPlayer(p.getX() + 0.5D, p.getY() + 0.5D, p.getZ() + 0.5D, PLAYER_SEARCH_RADIUS, false);
    }
}
