package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.core.UtilClean;
import ca.bradj.questown.jobs.production.RoomsNeedingVillagerInput;
import ca.bradj.questown.jobs.production.RoomsNeedingVillagerInput.NVIRoom;
import ca.bradj.questown.logic.IPredicateCollection;
import ca.bradj.questown.logic.PredicateCollection;
import ca.bradj.questown.town.workstatus.State;
import ca.bradj.roomrecipes.adapter.IRoomRecipeMatch;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

class RoomsStatusLogic {

    /**
     * Computes which rooms need villager input based on the current state of job blocks.
     *
     * @param jobRooms          The rooms that are valid job sites
     * @param getJobBlockState  Function to get the current state of a job block
     * @param canClaim          Predicate to check if a position can be claimed for work
     * @param isJobBlock        Predicate to check if a position is a job block
     * @param getIngredients    Function to get required ingredients for a state (null or empty if none required)
     * @param getQuantityForStep Function to get required ingredient quantity for a state (null if none required)
     * @param getTools          Function to get required tools for a state (null or empty if none required)
     * @param getContainedBlocks Function to get all block positions in a room
     * @param maxState          The maximum processing state for this job
     */
    static <
            RL,
            POS,
            HELD_ITEM,
            TOWN_ITEM,
            ROOM,
            MATCH
            >
    RoomsNeedingVillagerInput<ROOM, RL, POS> compute(
            Collection<MATCH> jobRooms,
            Function<POS, State> getJobBlockState,
            Predicate<POS> canClaim,
            Predicate<POS> isJobBlock,
            Function<Integer, @Nullable PredicateCollection<HELD_ITEM, HELD_ITEM>> getIngredients,
            Function<Integer, @Nullable Integer> getQuantityForStep,
            Function<Integer, @Nullable PredicateCollection<TOWN_ITEM, TOWN_ITEM>> getTools,
            Function<MATCH, Collection<POS>> getContainedBlocks,
            BiFunction<MATCH, Boolean, NVIRoom<ROOM, RL, POS>> maker,
            int maxState
    ) {
        // TODO: Reduce duplication with MCTownStateWorldInteraction.hasSupplies
        HashMap<Integer, List<NVIRoom<ROOM, RL, POS>>> b = new HashMap<>();

        // Process states that require ingredients
        for (int state = 0; state < maxState; state++) {
            PredicateCollection<HELD_ITEM, HELD_ITEM> ingrs = getIngredients.apply(state);
            if (ingrs == null || ingrs.isEmpty()) {
                continue;
            }
            Collection<MATCH> matches = roomsWithState(
                    jobRooms, getJobBlockState, state, isJobBlock, s -> true
            );
            Integer stateQty = getQuantityForStep.apply(state);
            ArrayList<MATCH> rwwcbd = getRoomsWhereWorkCanBeDone(
                    getJobBlockState,
                    canClaim,
                    matches,
                    stateQty,
                    getContainedBlocks
            );
            UtilClean.addAllOrInitializeList(b, state, rwwcbd.stream().map(v -> maker.apply(v, false)).toList());
        }

        // Process states that require tools
        HashMap<Integer, IPredicateCollection<?>> stateTools = new HashMap<>();
        boolean requiresTools = false;
        for (int i = 0; i < maxState; i++) {
            PredicateCollection<TOWN_ITEM, TOWN_ITEM> tool = getTools.apply(i);
            if (tool != null && !tool.isEmpty()) {
                requiresTools = true;
                stateTools.put(i, tool);
            }
        }

        Set<Integer> statesRequiringTools = stateTools.keySet();
        for (Integer toolState : statesRequiringTools) {
            // Tools gathered at a state are needed for all remaining states (they carry forward)
            for (int i = toolState; i < maxState; i++) {
                Collection<MATCH> list = roomsWithState(
                        jobRooms, getJobBlockState, i, isJobBlock, s -> true
                );
                // Key by the room's actual processing state (i), not the tool state
                UtilClean.addAllOrInitializeList(b, i, list.stream().map(v -> maker.apply(v, false)).toList());
            }
        }

        // If no tools are required, also include rooms where work can be done
        if (!requiresTools) {
            for (int i = 0; i < maxState; i++) {
                Collection<MATCH> workable = roomsWithState(jobRooms, getJobBlockState, i, isJobBlock, State::hasWorkLeft);
                UtilClean.addAllOrInitializeList(b, i, workable.stream().map(v -> maker.apply(v, true)).toList());
            }
        }
        return new RoomsNeedingVillagerInput<>(ImmutableMap.copyOf(b));
    }

    /**
     * Legacy method that takes DeclarativeJobChecks directly.
     * Used by DeclarativeJob.roomsNeedingIngredientsOrTools and tests.
     */
    static <
            EXTRA,
            RL,
            POS,
            HELD_ITEM,
            TOWN_ITEM,
            ROOM,
            MATCH extends IRoomRecipeMatch<ROOM, RL, POS, ?>
            >
    RoomsNeedingVillagerInput<ROOM, RL, POS> compute(
            Collection<MATCH> jobRooms,
            Function<POS, State> getJobBlockState,
            Predicate<POS> canClaim,
            Predicate<POS> isJobBlock,
            DeclarativeJobChecks<EXTRA, HELD_ITEM, TOWN_ITEM, RoomRecipeMatch<ROOM>, POS> checks,
            Function<MATCH, Collection<POS>> getContainedBlocks,
            int maxState
    ) {
        return compute(
                jobRooms,
                getJobBlockState,
                canClaim,
                isJobBlock,
                checks::getIngredientsForStep,
                state -> checks.getQuantityForStep(state, null),
                checks::getToolsForStep,
                getContainedBlocks,
                NVIRoom::new,
                maxState
        );
    }

    @SuppressWarnings("unchecked")
    private static <POS, MATCH> Collection<MATCH> roomsWithState(
            Collection<MATCH> rooms,
            Function<POS, State> getJobBlockState,
            Integer state,
            Predicate<POS> isJobBlock,
            Predicate<State> extraCheck
    ) {
        // Cast needed because DeclarativeJobs.roomsWithState requires ROOM extends Room,
        // but we want this method to work with any ROOM type
        return (Collection<MATCH>) DeclarativeJobs.roomsWithState(
                (Collection) rooms,
                isJobBlock,
                (bp) -> {
                    State jbs = getJobBlockState.apply((POS) bp);
                    if (jbs == null) {
                        return false;
                    }
                    return state.equals(jbs.processingState()) && extraCheck.test(jbs);
                }
        );
    }

    private static @NotNull <MATCH, POS> ArrayList<MATCH> getRoomsWhereWorkCanBeDone(
            Function<POS, State> work,
            Predicate<POS> canClaim,
            Collection<MATCH> matches,
            Integer stateQty,
            Function<MATCH, Collection<POS>> getContainedBlocks
    ) {
        Stream<MATCH> roomz = matches
                .stream()
                .filter(room -> {
                    for (POS e : getContainedBlocks.apply(room)) {
                        State jobBlockState = work.apply(e);
                        if (jobBlockState == null) {
                            continue;
                        }
                        if (!canClaim.test(e)) {
                            continue;
                        }
                        if (jobBlockState.ingredientCount() < stateQty) {
                            return true;
                        }
                    }
                    return false;
                });
        ArrayList<MATCH> value = Lists.newArrayList(roomz.toList());
        return value;
    }
}