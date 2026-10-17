package ca.bradj.questown.jobs;

import ca.bradj.roomrecipes.core.Room;
import ca.bradj.roomrecipes.core.space.InclusiveSpace;
import ca.bradj.roomrecipes.core.space.Position;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 2026-10-17 playtest: the store room at walls x 104..107, z -61..-59 with its door at
 * (106, -61) in the north wall has a 2x1 interior; the chest fills (105, -60). The chest's
 * walk target used to be one step toward the door - (105, -61), inside the wall - and the
 * villager ended up standing on the chest, unable to use it.
 */
class InteractionSpotsTest {

    private static final int FLOOR_Y = 69;
    private static final BlockPos CHEST = new BlockPos(105, FLOOR_Y, -60);
    private static final BlockPos FREE_INTERIOR_CELL = new BlockPos(106, FLOOR_Y, -60);

    private static final Room STORE_ROOM = new Room(
            new Position(106, -61),
            InclusiveSpace.from(104, -61).to(107, -59)
    );

    @Test
    void doorDirectionForThePlaytestRoomIsNorth_soTheOldStepLandsInTheWall() {
        // Pins the failure: the old chest path used CHEST.relative(doorDirection) unchecked.
        assertEquals(Direction.NORTH, InteractionSpots.doorDirectionFromCenter(STORE_ROOM));
        assertEquals(new BlockPos(105, FLOOR_Y, -61), CHEST.relative(Direction.NORTH));
    }

    @Test
    void chestWhoseDoorSideIsAWall_getsTheFreeInteriorCell() {
        Set<BlockPos> standable = Set.of(FREE_INTERIOR_CELL);

        BlockPos spot = InteractionSpots.find(CHEST, STORE_ROOM, standable::contains);

        assertEquals(FREE_INTERIOR_CELL, spot);
    }

    @Test
    void doorSideIsPreferredWhenStandable() {
        BlockPos doorSide = CHEST.relative(Direction.NORTH);
        Set<BlockPos> standable = Set.of(doorSide, FREE_INTERIOR_CELL);

        assertEquals(doorSide, InteractionSpots.find(CHEST, STORE_ROOM, standable::contains));
    }
}
