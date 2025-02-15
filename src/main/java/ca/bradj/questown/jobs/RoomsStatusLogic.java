package ca.bradj.questown.jobs;

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

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class RoomsStatusLogic {
    public static <
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
        // TODO: Reduce duplication with MCTownStateWorldInteraction.hasSupplies
        HashMap<Integer, List<NVIRoom<ROOM, RL, POS>>> b = new HashMap<>();
        for (Map.Entry<Integer, PredicateCollection<HELD_ITEM, HELD_ITEM>> e : checks.getAllRequiredIngredients()
                                                                                     .entrySet()) {
            Integer state = e.getKey();
            PredicateCollection<HELD_ITEM, HELD_ITEM> ingrs = e.getValue();
            if (ingrs.isEmpty()) {
                b.put(state, new ArrayList<>());
                break;
            }
            Collection<MATCH> matches = roomsWithState(
                    jobRooms, getJobBlockState, state, isJobBlock, s -> true
            );
            Integer stateQty = checks.getQuantityForStep(state, null);
            ArrayList<MATCH> rwwcbd = getRoomsWhereWorkCanBeDone(
                    getJobBlockState,
                    canClaim,
                    matches,
                    stateQty,
                    getContainedBlocks
            );
            UtilClean.addAllOrInitialize(b, state, rwwcbd.stream().map(v -> new NVIRoom<>(v, false)).toList());
        }
        HashMap<Integer, IPredicateCollection<?>> stateTools = new HashMap<>();
        boolean requiresTools = false;
        Map<Integer, PredicateCollection<TOWN_ITEM, TOWN_ITEM>> rt = checks.getAllRequiredTools();
        if (rt.values()
              .stream()
              .anyMatch(v -> !v.isEmpty())) {
            for (int i = 0; i < maxState; i++) {
                PredicateCollection<TOWN_ITEM, TOWN_ITEM> tool = RoomsStatusLogic.getToolOrDefault(rt, i);
                if (!tool.isEmpty()) {
                    requiresTools = true;
                }
                stateTools.put(i, tool);
            }
        }
        Set<Integer> statesRequiringTools = stateTools.keySet();
        for (Integer state : statesRequiringTools) {
            // Hold on to tools that are required at this state and any previous states
            for (int i = 0; i <= state; i++) {
                Collection<MATCH> list = roomsWithState(
                        jobRooms, getJobBlockState, i, isJobBlock, s -> true
                );
                UtilClean.addAllOrInitialize(b, state, list.stream().map(v -> new NVIRoom<>(v, false)).toList());
            }
        }
        if (!requiresTools) {
            for (int i = 0; i < maxState; i++) {
                Collection<MATCH> workable = roomsWithState(
                        jobRooms, getJobBlockState, i, isJobBlock, State::hasWorkLeft
                );
                UtilClean.addAllOrInitialize(b, i, workable.stream().map(v -> new NVIRoom<>(v, true)).toList());
            }
        }
        return new RoomsNeedingVillagerInput<>(ImmutableMap.copyOf(b));
    }

    private static <ROOM, POS, MATCH extends IRoomRecipeMatch<ROOM, ?, POS, ?>> Collection<MATCH> roomsWithState(
            Collection<MATCH> rooms,
            Function<POS, State> getJobBlockState,
            Integer state,
            Predicate<POS> isJobBlock,
            Predicate<State> extraCheck
    ) {
        return JobsClean.roomsWithState(
                rooms,
                isJobBlock,
                (bp) -> {
                    State jbs = getJobBlockState.apply(bp);
                    if (jbs == null) {
                        return false;
                    }
                    return state.equals(jbs.processingState()) && extraCheck.test(jbs);
                }
        );
    }


    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <COLL extends PredicateCollection<?, ?>> COLL getToolOrDefault(
            Map<Integer, COLL> checks,
            int i
    ) {
        PredicateCollection noToolDefined = PredicateCollection.empty("no tool defined");
        return checks.getOrDefault(i, (COLL) noToolDefined);
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
