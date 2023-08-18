package ca.bradj.questown.logic;

import ca.bradj.questown.Questown;
import ca.bradj.questown.core.Config;
import ca.bradj.roomrecipes.adapter.Positions;
import ca.bradj.roomrecipes.core.Room;
import ca.bradj.roomrecipes.core.space.Position;
import ca.bradj.roomrecipes.logic.DoorDetection;
import ca.bradj.roomrecipes.logic.LevelRoomDetection;
import ca.bradj.roomrecipes.logic.interfaces.WallDetector;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Optional;

public class TownCycle {

    public interface BlockChecker {

        boolean IsEmpty(Position dp);
        boolean IsWall(Position dp);
        boolean IsDoor(Position dp);

    }
    public static ImmutableMap<Position, Optional<Room>> findRooms(
            @Nullable Position scanAroundPosition,
            BlockChecker checker,
            Iterable<Position> registeredDoors
    ) {
        ImmutableCollection<Position> foundDoors = ImmutableList.of();
        if (scanAroundPosition != null) {
            foundDoors = findDoors(checker, scanAroundPosition);
        }
        ImmutableList.Builder<Position> b = ImmutableList.builder();
        ImmutableList<Position> allDoors = b.addAll(foundDoors).addAll(registeredDoors).build();
        return LevelRoomDetection.findRooms(
                allDoors, 20, checker::IsWall
        );
    }

    private static ImmutableCollection<Position> findDoors(
            BlockChecker blocks,
            Position townBlockPosition
    ) {
        Questown.LOGGER.trace("Checking for doors");
        Collection<Position> doors = DoorDetection.LocateDoorsAroundPosition(
                townBlockPosition, (Position dp) -> {
                    if (blocks.IsEmpty(dp)) {
                        return false;
                    }
                    return blocks.IsDoor(dp);
                },
                Config.DOOR_SEARCH_RADIUS.get()
        );
        return ImmutableList.copyOf(doors);
    }


    public static Optional<BlockPos> findCampfire(
            BlockPos pos, Level level
    ) {
        // TODO: Move to RoomRecipes?

        int radius = Config.CAMPFIRE_SEARCH_RADIUS.get();
        for(int z = -radius; z < radius; ++z) {
            for(int x = -radius; x < radius; ++x) {
                BlockPos cfPos = pos.offset(x, 0, z);
                if (level.getBlockState(cfPos).getBlock().equals(Blocks.CAMPFIRE)) {
                    return Optional.of(cfPos);
                }
            }
        }

        return Optional.empty();
    }

    public static @Nullable BlockPos findTownGate(
            BlockPos welcomePos,
            ServerLevel level,
            WallDetector wd
    ) {
        BlockPos offsetX1 = welcomePos.offset(1, 0, 0);
        BlockPos offsetX2 = welcomePos.offset(-1, 0, 0);
        BlockPos offsetZ1 = welcomePos.offset(0, 0, 1);
        BlockPos offsetZ2 = welcomePos.offset(0, 0, -1);

        if (wd.IsWall(Positions.FromBlockPos(offsetX1)) && wd.IsWall(Positions.FromBlockPos(offsetX2))) {
            if (hasTorches(level, offsetX1, offsetX2)) {
                return welcomePos;
            }
        }

        if (wd.IsWall(Positions.FromBlockPos(offsetZ1)) && wd.IsWall(Positions.FromBlockPos(offsetZ2))) {
            if (hasTorches(level, offsetZ1, offsetZ2)) {
                return welcomePos;
            }
        }
        return null;
    }

    private static boolean hasTorches(
            ServerLevel level,
            BlockPos offsetX1,
            BlockPos offsetX2
    ) {
        BlockPos torchPos1 = offsetX1.above().offset(0, 0, 1);
        BlockPos torchPos2 = offsetX1.above().offset(0, 0, -1);
        BlockPos torchPos3 = offsetX2.above().offset(0, 0, 1);
        BlockPos torchPos4 = offsetX2.above().offset(0, 0, -1);
        if (offsetX1.getX() == offsetX2.getX()) {
            torchPos1 = offsetX1.above().offset(1, 0, 0);
            torchPos2 = offsetX1.above().offset(-1, 0, 0);
            torchPos3 = offsetX2.above().offset(1, 0, 0);
            torchPos4 = offsetX2.above().offset(-1, 0, 0);
        }
        return level.getBlockState(torchPos1).is(Blocks.WALL_TORCH) &&
                level.getBlockState(torchPos2).is(Blocks.WALL_TORCH) &&
                level.getBlockState(torchPos3).is(Blocks.WALL_TORCH) &&
                level.getBlockState(torchPos4).is(Blocks.WALL_TORCH);
    }


}
