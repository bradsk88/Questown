package ca.bradj.questown.logic;

import ca.bradj.questown.Questown;
import ca.bradj.questown.core.Config;
import ca.bradj.roomrecipes.core.Room;
import ca.bradj.roomrecipes.core.space.Position;
import ca.bradj.roomrecipes.logic.DoorDetection;
import ca.bradj.roomrecipes.logic.LevelRoomDetection;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import java.util.Collection;
import java.util.Optional;

public class TownCycle {

    public interface BlockChecker {
        boolean IsEmpty(Position dp);
        boolean IsWall(Position dp);

        boolean IsDoor(Position dp);
    }

    public static ImmutableMap<Position, Optional<Room>> findRooms(
            Position townBlockPosition,
            BlockChecker checker
    ) {
        Collection<Position> foundDoors = findDoors(checker, townBlockPosition);
        return LevelRoomDetection.findRooms(
                foundDoors, 20, checker::IsWall
        );
    }

    private static Collection<Position> findDoors(
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
        return doors;
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


}
