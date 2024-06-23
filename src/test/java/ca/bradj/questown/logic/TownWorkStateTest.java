package ca.bradj.questown.logic;

import ca.bradj.questown.jobs.production.Valued;
import ca.bradj.questown.town.workstatus.State;
import ca.bradj.roomrecipes.core.Room;
import ca.bradj.roomrecipes.core.space.InclusiveSpace;
import ca.bradj.roomrecipes.core.space.Position;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

class TownWorkStateTest {

    private static final Room arbitraryRoom = new Room(
            new Position(0, 1),
            new InclusiveSpace(new Position(-1, -1), new Position(1, 1))
    );

    private record TestStatus(
            int value
    ) implements Valued<TestStatus> {
        @Override
        public TestStatus minusValue(int i) {
            return new TestStatus(value - i);
        }
    }

    @Test
    void updateForSpecialRooms_shouldSetNullStatesToFresh() {
        Map<Position, State> states = new HashMap<>();
        TownWorkState.updateForSpecialRooms(
                () -> ImmutableMap.of(
                        new TestStatus(1), ImmutableList.of(arbitraryRoom)
                ),
                (p, r) -> p.x == 0 && p.z == 0,
                s -> 0,
                (p, r) -> states.get(p),
                (p, r, s) -> states.put(p, s),
                s -> false,
                ImmutableMap.of()
        );
        Assertions.assertEquals(State.fresh(), states.get(new Position(0, 0)));
    }

    @Test
    void updateForSpecialRooms_shouldSetNullStatesToFresh_AndSetWork() {
        Map<Position, State> states = new HashMap<>();
        TownWorkState.updateForSpecialRooms(
                () -> ImmutableMap.of(
                        new TestStatus(1), ImmutableList.of(arbitraryRoom)
                ),
                (p, r) -> p.x == 0 && p.z == 0,
                s -> 15,
                (p, r) -> states.get(p),
                (p, r, s) -> states.put(p, s),
                ImmutableMap.<TestStatus, Boolean>of()::get,
                ImmutableMap.of()
        );
        Assertions.assertEquals(15, states.get(new Position(0, 0)).workLeft());
    }

    @Test
    void updateForSpecialRooms_shouldKeepStates_IfSuppliesPresent() {
        Map<Position, State> states = new HashMap<>();
        int initialValue = 4;
        State initialState = State.freshAtState(initialValue);
        states.put(new Position(0, 0), initialState);

        ImmutableMap<TestStatus, Boolean> suppliesRequired = ImmutableMap.of(
                new TestStatus(initialValue), true
        );
        ImmutableMap<TestStatus, Boolean> supplies = ImmutableMap.of(
                new TestStatus(initialValue), true
        );

        TownWorkState.updateForSpecialRooms(
                () -> ImmutableMap.of(new TestStatus(initialValue), ImmutableList.of(arbitraryRoom)),
                (p, r) -> p.x == 0 && p.z == 0,
                s -> 0,
                (p, r) -> states.get(p),
                (p, r, s) -> states.put(p, s),
                suppliesRequired::get,
                supplies
        );
        Assertions.assertEquals(initialState, states.get(new Position(0, 0)));
    }

    @Test
    void updateForSpecialRooms_shouldZeroStates_IfSuppliesMissing() {
        Map<Position, State> states = new HashMap<>();
        int initialValue = 4;
        State initialState = State.freshAtState(initialValue);
        states.put(new Position(0, 0), initialState);

        ImmutableMap<TestStatus, Boolean> suppliesRequired = ImmutableMap.of(
                new TestStatus(initialValue), true
        );
        ImmutableMap<TestStatus, Boolean> suppliesMissing = ImmutableMap.of(
                new TestStatus(initialValue), false
        );

        TownWorkState.updateForSpecialRooms(
                () -> ImmutableMap.of(new TestStatus(initialValue), ImmutableList.of(arbitraryRoom)),
                (p, r) -> p.x == 0 && p.z == 0,
                s -> 0,
                (p, r) -> states.get(p),
                (p, r, s) -> states.put(p, s),
                suppliesRequired::get,
                suppliesMissing
        );
        Assertions.assertEquals(State.fresh(), states.get(new Position(0, 0)));
    }

    @Test
    void updateForSpecialRooms_shouldDropStatesToSuppliedState_IfSuppliesMissingAtCurrentState() {
        Map<Position, State> states = new HashMap<>();
        int initialValue = 4;
        State initialState = State.freshAtState(initialValue);
        states.put(new Position(0, 0), initialState);

        ImmutableMap<TestStatus, Boolean> suppliesRequired = ImmutableMap.of(
                new TestStatus(2), true,
                new TestStatus(3), true,
                new TestStatus(4), true
        );
        ImmutableMap<TestStatus, Boolean> supplies = ImmutableMap.of(
                new TestStatus(2), true,
                new TestStatus(3), false,
                new TestStatus(4), false
        );

        TownWorkState.updateForSpecialRooms(
                () -> ImmutableMap.of(new TestStatus(initialValue), ImmutableList.of(arbitraryRoom)),
                (p, r) -> p.x == 0 && p.z == 0,
                s -> 0,
                (p, r) -> states.get(p),
                (p, r, s) -> states.put(p, s),
                suppliesRequired::get,
                supplies
        );
        Assertions.assertEquals(State.freshAtState(2), states.get(new Position(0, 0)));
    }

    @Test
    void updateForSpecialRooms_shouldNotDropStatesToSuppliedState_IfSuppliesMissingAtCurrentStateButNotRequired() {
        Map<Position, State> states = new HashMap<>();
        int initialValue = 4;
        State initialState = State.freshAtState(initialValue);
        states.put(new Position(0, 0), initialState);

        ImmutableMap<TestStatus, Boolean> suppliesRequired = ImmutableMap.of(
                new TestStatus(2), true,
                new TestStatus(3), false,
                new TestStatus(4), false
        );
        ImmutableMap<TestStatus, Boolean> supplies = ImmutableMap.of(
                new TestStatus(2), true,
                new TestStatus(3), false,
                new TestStatus(4), false
        );

        TownWorkState.updateForSpecialRooms(
                () -> ImmutableMap.of(new TestStatus(initialValue), ImmutableList.of(arbitraryRoom)),
                (p, r) -> p.x == 0 && p.z == 0,
                s -> 0,
                (p, r) -> states.get(p),
                (p, r, s) -> states.put(p, s),
                suppliesRequired::get,
                supplies
        );
        Assertions.assertEquals(State.freshAtState(4), states.get(new Position(0, 0)));
    }
}