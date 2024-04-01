package ca.bradj.questown.jobs;

import com.google.common.collect.ImmutableMap;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

public class JobRequirements {
    public static <ROOM> Map<Integer, ? extends List<ROOM>> roomsWhereSpecialRulesApply(
            int maxState,
            Function<Integer, ? extends Collection<String>> specialRules,
            Supplier<List<ROOM>> roomsWithWorkResultsInStorage
    ) {
        ImmutableMap.Builder<Integer, List<ROOM>> b = ImmutableMap.builder();

        for (int i = 0; i < maxState; i++) {
            Collection<String> rules = specialRules.apply(i);if (rules.contains(SpecialRules.INGREDIENT_ANY_VALID_WORK_OUTPUT)) {
                List<ROOM> rooms = roomsWithWorkResultsInStorage.get();
                if (rooms != null && !rooms.isEmpty()) {
                    b.put(i, rooms);
                }
            }

        }
        return b.build();
    }
}
