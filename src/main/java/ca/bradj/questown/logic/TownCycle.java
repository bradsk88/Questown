package ca.bradj.questown.logic;

import ca.bradj.questown.Questown;
import ca.bradj.questown.adapter.Positions;
import ca.bradj.questown.core.space.InclusiveSpace;
import ca.bradj.questown.core.space.Position;

import java.util.Collection;

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
        void newRoomDetected(InclusiveSpace space);
    }

    public static void townTick(
            Position townBlockPosition,
            BlockChecker checker,
            Collection<RoomDetector> currentDoors,
            DoorsListener doors,
            NewRoomHandler roomHandler
    ) {
        findDoors(checker, townBlockPosition, doors);
        for (RoomDetector rd : currentDoors) {
            Position doorPos = rd.getDoorPos();
            if (checker.IsEmpty(doorPos)) {
                doors.DoorRemoved(doorPos);
                Questown.LOGGER.debug("Removed door at pos " + doorPos);
                continue;
            }
            Questown.LOGGER.trace("Updating around door" + doorPos);
            boolean wasRoom = rd.isRoom();
            rd.update((Position dp) -> !checker.IsEmpty(dp));

            if (rd.isRoom() && !wasRoom) {
                Questown.LOGGER.debug("Room detected");
                Questown.LOGGER.debug("Corners: " + rd.getCorners());
                InclusiveSpace space = Positions.getInclusiveSpace(rd.getCorners());
                roomHandler.newRoomDetected(space);
            } else if (wasRoom && !rd.isRoom()) {
                Questown.LOGGER.debug("Room destroyed");
            }
        }
    }

    private static void findDoors(
            BlockChecker blocks,
            Position townBlockPosition,
            DoorsListener dl
    ) {
        Questown.LOGGER.info("Checking for doors");
        Collection<Position> doors = DoorDetection.LocateDoorsAroundPosition(
                townBlockPosition, (Position dp) -> {
                    if (blocks.IsEmpty(dp)) {
                        return false;
                    }
                    return blocks.IsDoor(dp);
                },
                10 // TODO: Constant or parameter
        );
        doors.forEach(dp -> {
            Questown.LOGGER.debug("Door detected at " + dp);
            dl.DoorAdded(dp);
        });
    }

}
