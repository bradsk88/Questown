package ca.bradj.questown.jobs;

import ca.bradj.questown.logic.IPredicateCollection;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

/**
 * A job's work output: a held item that no state of the job uses as an ingredient or tool. The
 * status circuit's "drop loot before more work" branch fires on it ({@code holdsWorkOutput}), and
 * the loot drop takes only it, so a townie keeps its own supplies (a hunter's sword, a BOP
 * depositor's Block of Progress) while it stores the rest (r16 #9: the drop emptied every item and
 * stranded deposited BOPs in the storeroom). Pinned by {@code HeldWorkOutputTest}.
 */
public final class HeldWorkOutput<H extends HeldItem<H, I>, I extends Item<I>> {

    private final List<IPredicateCollection<H>> ingredients;
    private final List<IPredicateCollection<I>> tools;

    private HeldWorkOutput(
            List<IPredicateCollection<H>> ingredients,
            List<IPredicateCollection<I>> tools
    ) {
        this.ingredients = ingredients;
        this.tools = tools;
    }

    public static <H extends HeldItem<H, I>, I extends Item<I>> HeldWorkOutput<H, I> ofStates(
            int maxState,
            IntFunction<? extends @Nullable IPredicateCollection<H>> ingredientsAt,
            IntFunction<? extends @Nullable IPredicateCollection<I>> toolsAt
    ) {
        List<IPredicateCollection<H>> ingredients = new ArrayList<>();
        List<IPredicateCollection<I>> tools = new ArrayList<>();
        for (int s = 0; s <= maxState; s++) {
            addIfPresent(ingredients, ingredientsAt.apply(s));
            addIfPresent(tools, toolsAt.apply(s));
        }
        return new HeldWorkOutput<>(ingredients, tools);
    }

    private static <T> void addIfPresent(
            List<IPredicateCollection<T>> into,
            @Nullable IPredicateCollection<T> p
    ) {
        if (p == null || p.isEmpty()) {
            return;
        }
        into.add(p);
    }

    public boolean isOutput(H held) {
        if (held.isEmpty()) {
            return false;
        }
        if (ingredients.stream().anyMatch(p -> p.test(held))) {
            return false;
        }
        return tools.stream().noneMatch(p -> p.test(held.get()));
    }

    public boolean anyOutput(Iterable<H> held) {
        for (H h : held) {
            if (isOutput(h)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The items a loot drop should store: the work output, or, with none in hand, every non-empty
     * item. The fallback keeps the drop branches that fire without output ("drop loot when no work
     * possible" returns unused supplies) from stalling in DROPPING_LOOT with full hands.
     */
    public List<H> itemsToDrop(Iterable<H> held) {
        ImmutableList.Builder<H> output = ImmutableList.builder();
        ImmutableList.Builder<H> all = ImmutableList.builder();
        for (H h : held) {
            if (h.isEmpty()) {
                continue;
            }
            all.add(h);
            if (isOutput(h)) {
                output.add(h);
            }
        }
        List<H> outputOnly = output.build();
        if (!outputOnly.isEmpty()) {
            return outputOnly;
        }
        return all.build();
    }
}
