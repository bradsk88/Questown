package ca.bradj.questown.town.rooms;

import ca.bradj.roomrecipes.core.space.InclusiveSpace;
import ca.bradj.roomrecipes.core.space.Position;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Optional;

/**
 * Playtest r12 #6 (dev 2026-10-23): a townie with no free floor beside its bed stands in the
 * bedroom's doorway.
 */
class RoomDoorsTest {

    // Walls x -12..-8, z 131..135; door in the south wall; floor-level Y 69.
    private static final MCRoom BEDROOM = new MCRoom(
            new Position(-10, 135),
            ImmutableList.of(InclusiveSpace.from(-12, 131).to(-8, 135)),
            69
    );
    private static final MCRoom UPSTAIRS = new MCRoom(
            new Position(-12, 133),
            ImmutableList.of(InclusiveSpace.from(-12, 131).to(-8, 135)),
            73
    );

    @Test
    void bedInsideTheRoom_givesTheRoomsDoor() {
        Optional<BlockPos> door = RoomDoors.doorOfRoomContaining(
                ImmutableList.of(BEDROOM), new BlockPos(-9, 69, 132)
        );
        Assertions.assertEquals(Optional.of(new BlockPos(-10, 69, 135)), door);
    }

    @Test
    void roomOnAnotherFloor_isNotTheBedsRoom() {
        Optional<BlockPos> door = RoomDoors.doorOfRoomContaining(
                ImmutableList.of(UPSTAIRS, BEDROOM), new BlockPos(-9, 69, 132)
        );
        Assertions.assertEquals(Optional.of(new BlockPos(-10, 69, 135)), door);
    }

    @Test
    void bedOutsideEveryRoom_givesNothing() {
        Assertions.assertEquals(
                Optional.empty(),
                RoomDoors.doorOfRoomContaining(ImmutableList.of(BEDROOM), new BlockPos(0, 69, 0))
        );
    }

    // r23 #16 (dev 2026-10-30): a villager that gives up walking to a spot inside a room is told
    // which room it cannot get into.
    @Test
    void walkTargetInsideARoom_isThatRoom() {
        Optional<MCRoom> room = RoomDoors.roomHolding(
                ImmutableList.of(UPSTAIRS, BEDROOM), new BlockPos(-9, 69, 132)
        );
        Assertions.assertEquals(Optional.of(BEDROOM), room);
    }

    @Test
    void walkTargetOneAboveTheFloor_isStillThatRoom() {
        Optional<MCRoom> room = RoomDoors.roomHolding(
                ImmutableList.of(BEDROOM), new BlockPos(-9, 70, 132)
        );
        Assertions.assertEquals(Optional.of(BEDROOM), room);
    }

    @Test
    void walkTargetOutsideEveryRoom_isNoRoom() {
        Assertions.assertEquals(
                Optional.empty(),
                RoomDoors.roomHolding(ImmutableList.of(BEDROOM), new BlockPos(0, 69, 0))
        );
    }
}
