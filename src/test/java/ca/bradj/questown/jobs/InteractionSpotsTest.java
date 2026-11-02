package ca.bradj.questown.jobs;

import ca.bradj.roomrecipes.core.Room;
import ca.bradj.roomrecipes.core.space.InclusiveSpace;
import ca.bradj.roomrecipes.core.space.Position;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A storeroom whose chest's door side is a wall: the chest's walk target must be a standable cell. */
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

    /** A furnace built into a kitchen wall is used from inside the room. */
    @Test
    void jobBlockInAWall_getsTheInteriorNeighbour_notTheOutsideOne() {
        Room kitchen = new Room(new Position(-148, -151), InclusiveSpace.from(-149, -155).to(-147, -151));
        BlockPos furnace = new BlockPos(-147, FLOOR_Y, -154);
        BlockPos inside = new BlockPos(-148, FLOOR_Y, -154);
        BlockPos outside = new BlockPos(-146, FLOOR_Y, -154);

        BlockPos spot = InteractionSpots.find(furnace, kitchen, Set.of(inside, outside)::contains);

        assertEquals(inside, spot);
    }
}
