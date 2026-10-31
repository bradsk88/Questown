package ca.bradj.questown.jobs.declarative;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    // Locks in the contract behind the 2026-10-07 gate soft-lock diagnosis
    // (worlds 98/99 stall vs 96 pass): the rooms-needing-input map is EMPTY when
    // the job sees zero sites (stall branch), and NON-EMPTY when the gate mat room
    // is present with a pending state-0 block (pass branch). The stall fix must
    // therefore make the mat room visible to the site reader, not change the
    // needed-input semantics here.
    @Test
    void compute_emptyJobSites_yieldsEmptyNeededInput() {
        RoomsNeedingVillagerInput<String, String, Position> foundRooms = RoomsStatusLogic.compute(
                ImmutableList.of(),
                pos -> State.freshAtState(0),
                p -> true,
                p -> false,
                new DeclarativeJobChecks<Void, String, String, RoomRecipeMatch<String>, Position>(
                        ImmutableMap.of(0, PredicateCollections.fromSimpleEqualityCheck("bread")),
                        ImmutableMap.of(0, 1),
                        ImmutableMap.of(),
                        ImmutableMap.of(0, 0, 1, 1),
                        ImmutableMap.of(),
                        room -> true,
                        p -> true
                ),
                m -> ImmutableList.of(),
                2
        );
        assertTrue(foundRooms.getMatches().isEmpty());
        assertTrue(foundRooms.cleanFns(
                s -> ca.bradj.questown.mc.PredicateCollections.fromSimpleEqualityCheck("bread"),
                s -> ca.bradj.questown.mc.PredicateCollections.fromSimpleEqualityCheck("bread"),
                x -> null).isEmpty(),
            "no sites -> no needed ingredients (the shouldTakeItem 'needed-empty' reject)");
    }

    @Test
    void compute_gateMatRoom_present_state0_yieldsNeededIngredient() {
        Position matPos = new Position(-13, 64);
        Collection<IRoomRecipeMatch<String, String, Position, String>> jobRooms = ImmutableList.of(new IRoomRecipeMatch<String, String, Position, String>() {
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
                return ImmutableMap.of(matPos, "welcome_mat");
            }
        });

        Map<Position, State> jobStates = ImmutableMap.of(
                matPos, State.freshAtState(0).setWorkLeft(1)
        );

        RoomsNeedingVillagerInput<String, String, Position> foundRooms = RoomsStatusLogic.compute(
                jobRooms,
                jobStates::get,
                p -> true,
                matPos::equals,
                new DeclarativeJobChecks<Void, String, String, RoomRecipeMatch<String>, Position>(
                        ImmutableMap.of(0, PredicateCollections.fromSimpleEqualityCheck("villager_food")),
                        ImmutableMap.of(0, 1),
                        ImmutableMap.of(),
                        ImmutableMap.of(0, 0, 1, 1),
                        ImmutableMap.of(),
                        room -> true,
                        matPos::equals
                ),
                m -> m.getRoom().equals("gate") ? ImmutableList.of(matPos) : ImmutableList.of(),
                2
        );
        org.junit.jupiter.api.Assertions.assertFalse(foundRooms.getMatches().isEmpty(),
                "gate mat room at state 0 must be a needed-input site");
    }

    // ── Lock-ins (2026-10-07): the leaver ACCEPT path — cleanFns on the object the
    // leaver reads (roomsNeedingIngredientsOrTools) — verified live 2026-10-07:
    // stall world 401: cleanRooms read {0=[]} (60×) while fresh compute inner=[0=1]
    // (4×); pass world 400: inner=[0=1] (12×) and a non-empty read at the win moment.
    // These pin the object contract: a receptive state-0 bucket must survive copying
    // (which the cache path does: `new RoomsNeedingVillagerInput<>(rniot.get().get())`)
    // and flooring, and must yield a non-empty needed-item set that accepts the food.

    private static RoomsNeedingVillagerInput<String, String, Position> receptiveMat() {
        return matAt(0);
    }

    private static final Position MAT_POS = new Position(-13, 64);

    private static RoomsNeedingVillagerInput<String, String, Position> matAt(int state) {
        return matAt(state, p -> true, MAT_POS::equals);
    }

    private static RoomsNeedingVillagerInput<String, String, Position> matAt(
            int state,
            java.util.function.Predicate<Position> canClaim,
            java.util.function.Predicate<Position> isJobBlock
    ) {
        Collection<IRoomRecipeMatch<String, String, Position, String>> jobRooms = ImmutableList.of(new IRoomRecipeMatch<String, String, Position, String>() {
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
        });

        Map<Position, State> jobStates = ImmutableMap.of(
                MAT_POS, State.freshAtState(state)
        );

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

    @Test
    void cleanFns_state0Receptive_nonEmptyAndAcceptsFood() {
        RoomsNeedingVillagerInput<String, String, Position> rni = receptiveMat();

        var fns = rni.cleanFns(
                s -> Integer.valueOf(0).equals(s) ? PredicateCollections.fromSimpleEqualityCheck("villager_food") : null,
                s -> null,
                x -> x);

        org.junit.jupiter.api.Assertions.assertFalse(fns.isEmpty(),
                "a state-0 receptive mat must yield a non-empty needed-item set on the leaver ACCEPT path");

        boolean accepts = false;
        for (var pc : fns) {
            if (pc != null && pc.test("villager_food")) {
                accepts = true;
                break;
            }
        }
        assertTrue(accepts, "the needed-item predicates must accept the leaver's food item");
    }

    @Test
    void copyConstructor_preservesReceptiveState0Bucket() {
        // The cache path copies via `new RoomsNeedingVillagerInput<>(rniot.get().get())`.
        // A receptive state-0 bucket must survive that copy (stall signature was a
        // {0=[]} read while the fresh compute carried the mat in bucket 0).
        RoomsNeedingVillagerInput<String, String, Position> rni = receptiveMat();
        RoomsNeedingVillagerInput<String, String, Position> copy = new RoomsNeedingVillagerInput<>(rni.get());

        assertFalse(copy.cleanFns(
                s -> Integer.valueOf(0).equals(s) ? PredicateCollections.fromSimpleEqualityCheck("villager_food") : null,
                s -> null,
                x -> x).isEmpty(),
                "copy of a receptive rni must still yield needed items");
    }

    @Test
    void floor_preservesReceptiveState0Bucket() {
        RoomsNeedingVillagerInput<String, String, Position> rni = receptiveMat();
        RoomsNeedingVillagerInput<String, String, Position> floored = rni.floor();

        assertTrue(floored.containsKey(0), "floor() must keep a state-0 bucket that had rooms");
        assertFalse(floored.get(0).isEmpty(), "floor() must not empty the state-0 room list");
    }

    @Test
    void matAtWorkState1_notFoodReceptive() {
        // Locks the receptive-window semantics: during the mat's work phase
        // (state 1) it does NOT need the leaver's food, so a leaver reading this
        // state correctly REJECTS. The churn (leavers reject during work/time
        // phases) is therefore legitimate; the flake is failing to catch the
        // state-0 window - a timing/contention property, not a logic bug.
        RoomsNeedingVillagerInput<String, String, Position> rni = matAt(1);
        var fns = rni.cleanFns(
                s -> Integer.valueOf(0).equals(s) ? PredicateCollections.fromSimpleEqualityCheck("villager_food") : null,
                s -> null,
                x -> x);
        // state 1 requires work, not the state-0 food ingredient
        boolean needsFood = false;
        for (var pc : fns) {
            if (pc != null && pc.test("villager_food")) {
                needsFood = true;
                break;
            }
        }
        assertFalse(needsFood, "a mat in its work phase must not request the leaver's food");
    }

    @Test
    void cleanFns_allBucketsEmpty_yieldsEmptyNeededSet() {
        // The stalled read path: all buckets empty -> cleanFns empty -> shouldTakeItem
        // REJECTs (verified branch on-disk 2026-10-07, worlds 98/99/401).
        RoomsNeedingVillagerInput<String, String, Position> empty = new RoomsNeedingVillagerInput<>(ImmutableMap.of());

        assertTrue(empty.cleanFns(
                s -> Integer.valueOf(0).equals(s) ? PredicateCollections.fromSimpleEqualityCheck("villager_food") : null,
                s -> null,
                x -> x).isEmpty(),
                "empty rni must yield an empty needed set (REJECT branch)");
    }

    // ── Lock-in (2026-10-07): the stall is EXCLUSION of the gate mat, not the ACCEPT
    // logic. compute() (verified source) only fills a state-s bucket when isJobBlock(mat)
    // AND canClaim(mat) are both true alongside the declared state-s ingredient. Pin both
    // exclusion paths so a fix (whichever predicate the stall trips) is validated here:
    // an excluded mat must yield an empty needed set = the leaver's 'needed-empty' REJECT.

    @Test
    void matExcludedByIsJobBlock_yieldsEmptyNeededSet_neededEmptyReject() {
        RoomsNeedingVillagerInput<String, String, Position> rni = matAt(
                0,
                p -> true,   // canClaim: fine
                p -> false);  // isJobBlock: dropped

        assertTrue(rni.cleanFns(
                s -> Integer.valueOf(0).equals(s) ? PredicateCollections.fromSimpleEqualityCheck("villager_food") : null,
                s -> null,
                x -> x).isEmpty(),
                "an isJobBlock-excluded mat must yield an empty needed set (the leaver's needed-empty REJECT)");
    }

    @Test
    void matExcludedByCanClaim_yieldsEmptyNeededSet_neededEmptyReject() {
        RoomsNeedingVillagerInput<String, String, Position> rni = matAt(
                0,
                p -> false,  // canClaim: dropped
                MAT_POS::equals);                                       // isJobBlock: fine

        assertTrue(rni.cleanFns(
                s -> Integer.valueOf(0).equals(s) ? PredicateCollections.fromSimpleEqualityCheck("villager_food") : null,
                s -> null,
                x -> x).isEmpty(),
                "a canClaim-excluded mat must yield an empty needed set (the leaver's needed-empty REJECT)");
    }

    @Test
    void matAvailable_isJobBlockAndCanClaim_yieldsNonEmptyNeededSet_accepts() {
        RoomsNeedingVillagerInput<String, String, Position> rni = matAt(
                0,
                p -> true,   // canClaim: fine
                MAT_POS::equals);                                       // isJobBlock: fine

        var fns = rni.cleanFns(
                s -> Integer.valueOf(0).equals(s) ? PredicateCollections.fromSimpleEqualityCheck("villager_food") : null,
                s -> null,
                x -> x);

        assertFalse(fns.isEmpty(), "a fully-available receptive mat must yield a non-empty needed set (ACCEPT)");
    }

    // ── Lock-in (work-only start-gate, cook/stick stall): a job whose state 0 declares ONLY
    // work (cook_extract: work 0.1 → int 0, no tools, no ingredients) must see a FRESH block
    // (workLeft 0) in the state-0 work bucket. Before the fix the per-state filter was
    // State::hasWorkLeft, so a fresh zero-work block produced an empty map and the cook idled
    // in NO_JOBSITE.
    @Test
    void compute_workOnlyState0_freshBlock_yieldsWorkOnlyBucket() {
        Position furnacePos = new Position(0, 0);
        Collection<IRoomRecipeMatch<String, String, Position, String>> jobRooms = ImmutableList.of(new IRoomRecipeMatch<String, String, Position, String>() {
            public String getRecipeID() {
                return "kitchen_small";
            }

            @Override
            public ImmutableList<String> getRecipeIDs() {
                return ImmutableList.of(getRecipeID());
            }

            @Override
            public String getRoom() {
                return "kitchen_small";
            }

            @Override
            public ImmutableMap<Position, String> getContainedBlocks() {
                return ImmutableMap.of(furnacePos, "furnace");
            }
        });

        Map<Position, State> jobStates = ImmutableMap.of(
                furnacePos, State.freshAtState(0) // fresh: workLeft 0
        );

        RoomsNeedingVillagerInput<String, String, Position> foundRooms = RoomsStatusLogic.compute(
                jobRooms,
                jobStates::get,
                p -> true,
                furnacePos::equals,
                new DeclarativeJobChecks<Void, String, String, RoomRecipeMatch<String>, Position>(
                        ImmutableMap.of(),      // no ingredients
                        ImmutableMap.of(),       // no quantities
                        ImmutableMap.of(),       // no tools
                        ImmutableMap.of(0, 0),   // work-only state 0 (0, like cook_extract's floored 0.1)
                        ImmutableMap.of(),       // no time
                        room -> true,
                        furnacePos::equals
                ),
                m -> m.getRoom().equals("kitchen_small") ? ImmutableList.of(furnacePos) : ImmutableList.of(),
                1 // maxState: state 0 is the last state
        );

        java.util.Collection<RoomsNeedingVillagerInput.NVIRoom<String, String, Position>> rooms0 = foundRooms.get(0);
        assertFalse(rooms0.isEmpty(),
                "a work-only state-0 room with a fresh block must be visible in the work bucket");
        assertTrue(rooms0.stream().allMatch(RoomsNeedingVillagerInput.NVIRoom::dueToWorkOnly),
                "the state-0 admission must be flagged dueToWorkOnly");
    }
}