package ca.bradj.questown.gui;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.function.BiPredicate;

/**
 * The tools a skill card lists: those the job does not also use up (dev 2026-10-30: cook skills
 * showed "Tools Used: Raw Beef" beside "Items Used: Raw Beef"; the furnace template holds the meat
 * as state 0's tool before using it). Such an item is listed under Items Used only.
 */
public final class ToolsNotUsedUp {

    private ToolsNotUsedUp() {
    }

    public static <T> ImmutableList<T> of(
            List<T> tools,
            List<T> itemsUsed,
            BiPredicate<T, T> same
    ) {
        return tools.stream()
                    .filter(t -> itemsUsed.stream().noneMatch(i -> same.test(t, i)))
                    .collect(ImmutableList.toImmutableList());
    }
}
