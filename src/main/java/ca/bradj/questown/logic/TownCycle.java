package ca.bradj.questown.logic;

import ca.bradj.questown.Questown;
import ca.bradj.roomrecipes.core.Room;
import ca.bradj.roomrecipes.core.space.Position;
import ca.bradj.roomrecipes.logic.DoorDetection;
import ca.bradj.roomrecipes.logic.LevelRoomDetection;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public class TownCycle {

    public interface BlockChecker {
        boolean IsEmpty(Position dp);

        boolean IsDoor(Position dp);
    }

    public interface DoorsListener {
        void DoorAdded(Position dp);

        void DoorRemoved(Position dp);
    }

    public interface NewRoomHandler {
        void roomDestroyed(Position doorPos);
    }

    public interface RoomTicker {
        void roomTick(Room room);
    }

    public static void roomsTick(
            Position townBlockPosition,
            BlockChecker checker,
            DoorsListener doors,
            NewRoomHandler roomHandler,
            RoomTicker roomTicker
    ) {
        Collection<Position> foundDoors = findDoors(checker, townBlockPosition, doors);
        Map<Position, Optional<Room>> rooms = LevelRoomDetection.findRooms(
                foundDoors, 20, (position) -> !checker.IsEmpty(position)
        );
        rooms.forEach((position, room) -> {
            if (room.isEmpty()) {
                roomHandler.roomDestroyed(position);
            } else {
                roomTicker.roomTick(room.get());
            }
        });
    }

    private static Collection<Position> findDoors(
            BlockChecker blocks,
            Position townBlockPosition,
            DoorsListener dl
    ) {
        Questown.LOGGER.trace("Checking for doors");
        Collection<Position> doors = DoorDetection.LocateDoorsAroundPosition(
                townBlockPosition, (Position dp) -> {
                    if (blocks.IsEmpty(dp)) {
                        return false;
                    }
                    return blocks.IsDoor(dp);
                },
                100 // TODO: Constant or parameter
        );
        doors.forEach(dp -> {
            Questown.LOGGER.debug("Door detected at " + dp);
            dl.DoorAdded(dp);
        });
        return doors;
    }

}
