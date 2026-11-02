package ca.bradj.questown.gui;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.function.BiPredicate;

/** The tools a skill card lists: those the job does not also use up. */
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
