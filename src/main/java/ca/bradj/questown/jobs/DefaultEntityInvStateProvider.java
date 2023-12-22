package ca.bradj.questown.jobs;

import ca.bradj.questown.jobs.declarative.ProductionJournal;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class DefaultEntityInvStateProvider<
        INGREDIENT,
        TOWN_ITEM extends Item<TOWN_ITEM>,
        HELD_ITEM extends HeldItem<HELD_ITEM, TOWN_ITEM>
        > implements EntityInvStateProvider<Integer> {
    private final Supplier<ProductionJournal<TOWN_ITEM, HELD_ITEM>> journal;
    private final ImmutableMap<Integer, INGREDIENT> toolsRequiredAtStates;
    private final ImmutableMap<Integer, INGREDIENT> ingredientsRequiredAtStates;
    private final BiPredicate<INGREDIENT, HELD_ITEM> matchFn;
    private final Supplier<Map<Integer, ? extends Collection<?>>> roomsNeedingIngredientsOrTools;
    private final Function<Integer, ImmutableList<JobsClean.TestFn<TOWN_ITEM>>> recipe;

    public DefaultEntityInvStateProvider(
            Supplier<ProductionJournal<TOWN_ITEM, HELD_ITEM>> journal,
            ImmutableMap<Integer, INGREDIENT> ingredientsRequiredAtStates,
            ImmutableMap<Integer, INGREDIENT> toolsRequiredAtStates,
            Supplier<Map<Integer, ? extends Collection<?>>> roomsNeedingIngredientsOrTools,
            Function<Integer, ImmutableList<JobsClean.TestFn<TOWN_ITEM>>> recipe,
            BiPredicate<INGREDIENT, HELD_ITEM> matchFn
    ) {
        this.journal = journal;
        this.ingredientsRequiredAtStates = ingredientsRequiredAtStates;
        this.toolsRequiredAtStates = toolsRequiredAtStates;
        this.roomsNeedingIngredientsOrTools = roomsNeedingIngredientsOrTools;
        this.recipe = recipe;
        this.matchFn = matchFn;
    }

    @Override
    public boolean inventoryFull() {
        return journal.get().isInventoryFull();
    }

    @Override
    public boolean hasNonSupplyItems() {
        Set<Integer> statesToFeed = roomsNeedingIngredientsOrTools.get().entrySet().stream().filter(
                v -> !v.getValue().isEmpty()
        ).map(Map.Entry::getKey).collect(Collectors.toSet());
        ImmutableList<JobsClean.TestFn<TOWN_ITEM>> allFillableRecipes = ImmutableList.copyOf(
                statesToFeed.stream().flatMap(v -> recipe.apply(v).stream()).toList()
        );
        return JobsClean.hasNonSupplyItems(journal.get(), allFillableRecipes);
    }

    @Override
    public Map<Integer, Boolean> getSupplyItemStatus() {
        return DeclarativeJobs.getSupplyItemStatus(
                journal.get().getItems(),
                ingredientsRequiredAtStates,
                toolsRequiredAtStates,
                matchFn
        );
    }
}
