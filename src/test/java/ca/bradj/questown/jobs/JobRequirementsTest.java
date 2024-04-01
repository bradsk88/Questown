package ca.bradj.questown.jobs;

import ca.bradj.roomrecipes.core.Room;
import ca.bradj.roomrecipes.core.space.InclusiveSpace;
import ca.bradj.roomrecipes.core.space.Position;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class JobRequirementsTest {

    private final Room EXAMPLE_ROOM = new Room(
            new Position(1, 2),
            new InclusiveSpace(new Position(3, 4), new Position(5, 6))
    );

    @Test
    void roomsWhereSpecialRulesApply_shouldReturnRoomInState1_IfState1HasWorkResultRule_AndTownHasWorkResults() {
        ImmutableMap<Integer, ImmutableList<String>> definedRules = ImmutableMap.of(
                0, ImmutableList.of(), // No rules at 0
                1, ImmutableList.of(SpecialRules.INGREDIENT_ANY_VALID_WORK_OUTPUT)
        );
        Map<Integer, ? extends List<Room>> rooms = JobRequirements.roomsWhereSpecialRulesApply(
                2,
                definedRules::get,
                () -> ImmutableList.of(EXAMPLE_ROOM)
        );
        Assertions.assertFalse(rooms.containsKey(0));
        Assertions.assertTrue(rooms.containsKey(1));
        Assertions.assertEquals(1, rooms.get(1).size());
    }
}