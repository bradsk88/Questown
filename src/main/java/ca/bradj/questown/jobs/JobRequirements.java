package ca.bradj.questown.jobs;

import ca.bradj.questown.jobs.production.ProductionStatus;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

public class JobRequirements {
    public static <ROOM, STATUS> Map<ProductionStatus, ImmutableList<ROOM>> roomsWhereSpecialRulesApply(
            int maxState,
            Function<ProductionStatus, ? extends Collection<String>> specialRules,
            Supplier<? extends Collection<ROOM>> roomsWithWorkResultsInStorage,
            Supplier<? extends Collection<ROOM>> roomsWhereJobApplies,
            Function<ProductionStatus, Boolean> itemsHeldForState
    ) {
        ImmutableMap.Builder<ProductionStatus, ImmutableList<ROOM>> b = ImmutableMap.builder();

        for (int i = 0; i < maxState; i++) {
            ProductionStatus s = ProductionStatus.fromJobBlockStatus(i);
            Collection<String> rules = specialRules.apply(s);
            if (rules.contains(SpecialRules.INGREDIENT_ANY_VALID_WORK_OUTPUT)) {
                Collection<ROOM> rooms = new ArrayList<>();
                Collection<ROOM> rwwris = roomsWithWorkResultsInStorage.get();
                if (rwwris != null) {
                    rooms.addAll(rwwris);
                }
                if (Boolean.TRUE.equals(itemsHeldForState.apply(s))) {
                    rooms.addAll(roomsWhereJobApplies.get());
                }
                if (!rooms.isEmpty()) {
                    b.put(s, ImmutableList.copyOf(rooms));
                }
            }

        }
        return b.build();
    }
}
