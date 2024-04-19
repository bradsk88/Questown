package ca.bradj.questown.jobs;

import ca.bradj.questown.jobs.production.ProductionStatus;
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
        ImmutableMap<ProductionStatus, ImmutableList<String>> definedRules = ImmutableMap.of(
                ProductionStatus.fromJobBlockStatus(0), ImmutableList.of(), // No rules at 0
                ProductionStatus.fromJobBlockStatus(1), ImmutableList.of(SpecialRules.INGREDIENT_ANY_VALID_WORK_OUTPUT)
        );
        Map<ProductionStatus, ? extends List<Room>> rooms = JobRequirements.roomsWhereSpecialRulesApply(
                2,
                definedRules::get,
                () -> ImmutableList.of(EXAMPLE_ROOM), // Example room has work results
                () -> ImmutableList.of(EXAMPLE_ROOM), // Example room is a job site
                (state) -> false // Not holding any items
        );
        Assertions.assertFalse(rooms.containsKey(ProductionStatus.fromJobBlockStatus(0)));
        Assertions.assertTrue(rooms.containsKey(ProductionStatus.fromJobBlockStatus(1)));
        Assertions.assertEquals(1, rooms.get(ProductionStatus.fromJobBlockStatus(1)).size());
        Assertions.assertEquals(EXAMPLE_ROOM, rooms.get(ProductionStatus.fromJobBlockStatus(1)).get(0));
    }

    @Test
    void roomsWhereSpecialRulesApply_shouldReturnRoomInState1_IfState1HasWorkResultRule_AndRoomIsEmpty_ButEntityHasItem() {
        ImmutableMap<ProductionStatus, ImmutableList<String>> definedRules = ImmutableMap.of(
                ProductionStatus.fromJobBlockStatus(0), ImmutableList.of(), // No rules at 0
                ProductionStatus.fromJobBlockStatus(1), ImmutableList.of(SpecialRules.INGREDIENT_ANY_VALID_WORK_OUTPUT)
        );
        Map<ProductionStatus, ? extends List<Room>> rooms = JobRequirements.roomsWhereSpecialRulesApply(
                2,
                definedRules::get,
                ImmutableList::of, // No rooms have work results
                () -> ImmutableList.of(EXAMPLE_ROOM), // Example room is a job site
                (ProductionStatus state) -> state.value() == 1 // Entity is holding objects for state 1
        );
        Assertions.assertFalse(rooms.containsKey(ProductionStatus.fromJobBlockStatus(0)));
        Assertions.assertTrue(rooms.containsKey(ProductionStatus.fromJobBlockStatus(1)));
        Assertions.assertEquals(1, rooms.get(ProductionStatus.fromJobBlockStatus(1)).size());
        Assertions.assertEquals(EXAMPLE_ROOM, rooms.get(ProductionStatus.fromJobBlockStatus(1)).get(0));
    }

    @Test
    void roomsWhereSpecialRulesApply_shouldReturnEmpty_IfState1HasWorkResultRule_AndRoomIsEmpty_AndEntityIsMissingItem() {
        ImmutableMap<ProductionStatus, ImmutableList<String>> definedRules = ImmutableMap.of(
                ProductionStatus.fromJobBlockStatus(0), ImmutableList.of(), // No rules at 0
                ProductionStatus.fromJobBlockStatus(1), ImmutableList.of(SpecialRules.INGREDIENT_ANY_VALID_WORK_OUTPUT)
        );
        Map<ProductionStatus, ? extends List<Room>> rooms = JobRequirements.roomsWhereSpecialRulesApply(
                2,
                definedRules::get,
                ImmutableList::of, // No rooms have work results
                () -> ImmutableList.of(EXAMPLE_ROOM), // Example room is a job site
                state -> false // Entity has no items
        );
        Assertions.assertFalse(rooms.containsKey(ProductionStatus.fromJobBlockStatus(0)));
        Assertions.assertFalse(rooms.containsKey(ProductionStatus.fromJobBlockStatus(1)));
    }
}