package ca.bradj.questown.jobs;

import ca.bradj.questown.jobs.production.RoomsNeedingVillagerInput;
import ca.bradj.questown.mc.PredicateCollections;
import ca.bradj.questown.town.workstatus.State;
import ca.bradj.roomrecipes.adapter.IRoomRecipeMatch;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.roomrecipes.core.space.Position;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoomsStatusLogicTest {

    @Test
    void compute_shouldReturnRoomWithState1_IfWorkRemains_AndNoToolsRequired() {
        Position ovenPos = new Position(0, 0);
        Collection<IRoomRecipeMatch<String, String, Position, String>> jobRooms = ImmutableList.of(new IRoomRecipeMatch<String, String, Position, String>() {
            public String getRecipeID() {
                return "bakery";
            }

            @Override
            public ImmutableList<String> getRecipeIDs() {
                return ImmutableList.of(getRecipeID());
            }

            @Override
            public String getRoom() {
                return "bakery";
            }

            @Override
            public ImmutableMap<Position, String> getContainedBlocks() {
                return ImmutableMap.of(ovenPos, "oven");
            }
        });

        Map<Position, State> jobStates = ImmutableMap.of(
                ovenPos, State.freshAtState(1).setWorkLeft(1)
        );

        RoomsNeedingVillagerInput<String, String, Position> foundRooms = RoomsStatusLogic.compute(
                jobRooms,
                jobStates::get,
                p -> true,
                ovenPos::equals,
                new DeclarativeJobChecks<Void, String, String, RoomRecipeMatch<String>, Position>(
                        ImmutableMap.of(
                                0, PredicateCollections.fromSimpleEqualityCheck("bread")
                                // no ingredients required past state 0
                        ),
                        ImmutableMap.of(0, 1), // 1 bread required
                        ImmutableMap.of(
                                // no tools needed ever (important for this test)
                        ),
                        ImmutableMap.of(
                                0, 0,
                                1, 1 // Work required at state 1
                        ),
                        ImmutableMap.of(),
                        room -> true,
                        ovenPos::equals
                ),
                m -> m.getRoom().equals("bakery") ? ImmutableList.of(ovenPos) : ImmutableList.of(),
                2
        );
        assertEquals(1, foundRooms.getMatches().size());
    }
}