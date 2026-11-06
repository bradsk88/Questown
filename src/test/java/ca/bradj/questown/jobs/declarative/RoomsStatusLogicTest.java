package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.jobs.production.RoomsNeedingVillagerInput;
import ca.bradj.questown.logic.PredicateCollection;
import ca.bradj.questown.mc.PredicateCollections;
import ca.bradj.questown.town.workstatus.State;
import ca.bradj.roomrecipes.adapter.IRoomRecipeMatch;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.roomrecipes.core.space.Position;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Collection;
import java.util.Map;
import java.util.function.Predicate;

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
    private static final Position MAT_POS = new Position(-13, 64);

    private static RoomsNeedingVillagerInput<String, String, Position> matAt(int state) {
        return matAt(state, p -> true, MAT_POS::equals);
    }

    private static RoomsNeedingVillagerInput<String, String, Position> matAt(
            int state,
            Predicate<Position> canClaim,
            Predicate<Position> isJobBlock
    ) {
        Collection<IRoomRecipeMatch<String, String, Position, String>> jobRooms = ImmutableList.of(
                new IRoomRecipeMatch<String, String, Position, String>() {
                    public String getRecipeID() {
                        return "town_gate";
                    }

                    @Override
                    public ImmutableList<String> getRecipeIDs() {
                        return ImmutableList.of(getRecipeID());
                    }

                    @Override
                    public String getRoom() {
                        return "gate";
                    }

                    @Override
                    public ImmutableMap<Position, String> getContainedBlocks() {
                        return ImmutableMap.of(MAT_POS, "welcome_mat");
                    }
                }
        );

        Map<Position, State> jobStates = ImmutableMap.of(MAT_POS, State.freshAtState(state));

        return RoomsStatusLogic.compute(
                jobRooms,
                jobStates::get,
                canClaim,
                isJobBlock,
                new DeclarativeJobChecks<Void, String, String, RoomRecipeMatch<String>, Position>(
                        ImmutableMap.of(0, PredicateCollections.fromSimpleEqualityCheck("villager_food")),
                        ImmutableMap.of(0, 1),
                        ImmutableMap.of(),
                        ImmutableMap.of(0, 0, 1, 1),
                        ImmutableMap.of(),
                        room -> true,
                        MAT_POS::equals
                ),
                m -> m.getRoom().equals("gate") ? ImmutableList.of(MAT_POS) : ImmutableList.of(),
                3
        );
    }
    /** The items the rooms ask for, with food wanted at state 0. */
    private static ImmutableList<PredicateCollection<String, ?>> neededItems(
            RoomsNeedingVillagerInput<String, String, Position> rni
    ) {
        return rni.cleanFns(
                s -> Integer.valueOf(0).equals(s) ? PredicateCollections.fromSimpleEqualityCheck("villager_food") : null,
                s -> null,
                x -> x
        );
    }
    private static boolean asksForFood(RoomsNeedingVillagerInput<String, String, Position> rni) {
        return neededItems(rni).stream().anyMatch(pc -> pc != null && pc.test("villager_food"));
    }
    void matWaitingAtState0_asksForTheLeaversFood() {
        RoomsNeedingVillagerInput<String, String, Position> rni = matAt(0);

        assertFalse(neededItems(rni).isEmpty());
        assertTrue(asksForFood(rni));
    }
    void copyOfAWaitingMat_stillAsksForItems() {
        RoomsNeedingVillagerInput<String, String, Position> copy = new RoomsNeedingVillagerInput<>(matAt(0).get());

        assertFalse(neededItems(copy).isEmpty());
    }
    @Test
    void floor_keepsTheWaitingMatsState0Rooms() {
        RoomsNeedingVillagerInput<String, String, Position> floored = matAt(0).floor();

        assertTrue(floored.containsKey(0));
        assertFalse(floored.get(0).isEmpty());
    }

    @Test
    void matAtState1_needsWorkNotFood_soItDoesNotAskForFood() {
        assertFalse(asksForFood(matAt(1)));
    }

    @Test
    void noRooms_askForNothing() {
        assertTrue(neededItems(new RoomsNeedingVillagerInput<>(ImmutableMap.of())).isEmpty());
    }

    @ParameterizedTest(name = "jobBlock={0} claimable={1} -> asks={2}")
    @CsvSource({
            "false, true,  false",
            "true,  false, false",
            "true,  true,  true",
    })
    void mat_asksForItemsOnlyWhenItIsAJobBlockAndClaimable(
            boolean isJobBlock,
            boolean canClaim,
            boolean asks
    ) {
        RoomsNeedingVillagerInput<String, String, Position> rni = matAt(0, p -> canClaim, p -> isJobBlock);

        assertEquals(asks, !neededItems(rni).isEmpty());
    }

}