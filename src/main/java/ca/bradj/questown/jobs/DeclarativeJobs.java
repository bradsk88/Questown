package ca.bradj.questown.jobs;

import ca.bradj.questown.integration.minecraft.MCTownState;
import ca.bradj.questown.town.AbstractWorkStatusStore;
import ca.bradj.questown.town.Warper;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;

import static ca.bradj.questown.jobs.ProductionTimeWarper.getNextDaySegment;

public class DeclarativeJobs {
    public static <INGREDIENT, ITEM extends Item<ITEM>, HELD_ITEM extends HeldItem<HELD_ITEM, ITEM>> Map<Integer, Boolean> getSupplyItemStatus(
            Collection<HELD_ITEM> journalItems,
            ImmutableMap<Integer, INGREDIENT> ingredientsRequiredAtStates,
            ImmutableMap<Integer, INGREDIENT> toolsRequiredAtStates,
            BiPredicate<INGREDIENT, HELD_ITEM> matchFn
    ) {
        HashMap<Integer, Boolean> b = new HashMap<>();
        BiConsumer<Integer, INGREDIENT> fn = (state, ingr) -> {
            if (ingr == null) {
                if (!b.containsKey(state)) {
                    b.put(state, false);
                }
                return;
            }

            // The check passes if the worker has ALL the ingredients needed for the state
            boolean has = journalItems.stream().anyMatch(v -> matchFn.test(ingr, v));
            if (!b.getOrDefault(state, false)) {
                b.put(state, has);
            }
        };
        ingredientsRequiredAtStates.forEach(fn);
        toolsRequiredAtStates.forEach(fn);
        return ImmutableMap.copyOf(b);
    }

    public static Warper<MCTownState> warper(MCTownStateWorldInteraction wi) {
        return (liveState, currentTick, ticksPassed, villagerNum) -> {
            BlockPos fakePos = new BlockPos(villagerNum, villagerNum, villagerNum);

            MCTownState outState = liveState;

            AbstractWorkStatusStore.State state = liveState.workStates.get(fakePos);
            if (state == null) {
                outState = liveState.setJobBlockState(fakePos, AbstractWorkStatusStore.State.fresh());
            }

            long start = currentTick;
            long max = currentTick + ticksPassed;

            for (long i = start; i <= max; i = getNextDaySegment(i, max)) {
                state = liveState.workStates.get(fakePos);
                outState = wi.tryWorking(liveState, new WorkSpot<>(fakePos, state.processingState(), 1));
            }

            return outState;
        };
    }
}
