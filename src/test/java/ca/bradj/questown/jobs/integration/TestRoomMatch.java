package ca.bradj.questown.jobs.integration;

import ca.bradj.roomrecipes.adapter.IRoomRecipeMatch;
import ca.bradj.roomrecipes.core.Room;
import ca.bradj.roomrecipes.core.space.InclusiveSpace;
import ca.bradj.roomrecipes.core.space.Position;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * Test implementation of IRoomRecipeMatch for integration tests.
 */
public class TestRoomMatch implements IRoomRecipeMatch<Room, String, Position, String> {

    public final Room room;
    private final ImmutableList<String> recipeIds;
    public final ImmutableMap<Position, String> containedBlocks;

    public TestRoomMatch(Room room, ImmutableList<String> recipeIds, ImmutableMap<Position, String> containedBlocks) {
        this.room = room;
        this.recipeIds = recipeIds;
        this.containedBlocks = containedBlocks;
    }

    public static TestRoomMatch defaultRoom(String recipeId) {
        Position doorPos = new Position(0, 0);
        InclusiveSpace space = InclusiveSpace.from(0, 0).to(10, 10);
        Room room = new Room(doorPos, space);
        // Job block at the default workspot position
        return new TestRoomMatch(
                room,
                ImmutableList.of(recipeId),
                ImmutableMap.of(IntegrationTestWorld.DEFAULT_WORKSPOT_POS, "job_block")
        );
    }

    @Override
    public ImmutableList<String> getRecipeIDs() {
        return recipeIds;
    }

    @Override
    public Room getRoom() {
        return room;
    }

    @Override
    public ImmutableMap<Position, String> getContainedBlocks() {
        return containedBlocks;
    }
}
