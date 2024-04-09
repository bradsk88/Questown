package ca.bradj.questown.jobs;

import ca.bradj.questown.jobs.production.ProductionStatus;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

public class JobRequirements {
    public static <ROOM> Map<ProductionStatus, ImmutableList<ROOM>> roomsWhereSpecialRulesApply(
            int maxState,
            Function<ProductionStatus, ? extends Collection<String>> specialRules,
            Supplier<List<ROOM>> roomsWithWorkResultsInStorage
    ) {
        ImmutableMap.Builder<ProductionStatus, ImmutableList<ROOM>> b = ImmutableMap.builder();

        for (int i = 0; i < maxState; i++) {
            ProductionStatus s = ProductionStatus.fromJobBlockStatus(i);
            Collection<String> rules = specialRules.apply(s);
            if (rules.contains(SpecialRules.INGREDIENT_ANY_VALID_WORK_OUTPUT)) {
                List<ROOM> rooms = roomsWithWorkResultsInStorage.get();
                if (rooms != null && !rooms.isEmpty()) {
                    b.put(s, ImmutableList.copyOf(rooms));
                }
            }

        }
        return b.build();
    }
}
