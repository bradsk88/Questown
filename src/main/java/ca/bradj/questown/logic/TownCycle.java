package ca.bradj.questown.logic;

import ca.bradj.questown.Questown;
import ca.bradj.questown.adapter.Positions;
import ca.bradj.questown.core.space.InclusiveSpace;
import ca.bradj.questown.core.space.Position;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

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
        void roomDestroyed(Position doorPos, ImmutableSet<Position> space);
    }

    public interface RoomTicker {
        void roomTick(Position doorPos, InclusiveSpace inclusiveSpace);
    }

    public static void townTick(
            Position townBlockPosition,
            BlockChecker checker,
            Collection<RoomDetector> currentDoors,
            DoorsListener doors,
            NewRoomHandler roomHandler,
            RoomTicker roomTicker
    ) {
        findDoors(checker, townBlockPosition, doors);
        for (RoomDetector rd : ImmutableList.copyOf(currentDoors)) {
            Position doorPos = rd.getDoorPos();
            if (checker.IsEmpty(doorPos)) {
                doors.DoorRemoved(doorPos);
                Questown.LOGGER.debug("Removed door at pos " + doorPos);
                continue;
            }
            Questown.LOGGER.trace("Updating around door" + doorPos);
            boolean wasRoom = rd.isRoom();
            ImmutableSet<Position> oldCorners = rd.getCorners();
            rd.update((Position dp) -> !checker.IsEmpty(dp));

            if (rd.isRoom() && !wasRoom) {
                Questown.LOGGER.debug("Room detected");
                Questown.LOGGER.debug("Corners: " + rd.getCorners());
                InclusiveSpace space = Positions.getInclusiveSpace(rd.getCorners());
                roomHandler.newRoomDetected(space);
            } else if (wasRoom && !rd.isRoom()) {
                Questown.LOGGER.debug("Room destroyed");
                roomHandler.roomDestroyed(rd.getDoorPos(), oldCorners);
            }

            if (rd.isRoom()) {
                roomTicker.roomTick(rd.getDoorPos(), Positions.getInclusiveSpace(rd.getCorners()));
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
                30 // TODO: Constant or parameter
        );
        doors.forEach(dp -> {
            Questown.LOGGER.debug("Door detected at " + dp);
            dl.DoorAdded(dp);
        });
    }

}
