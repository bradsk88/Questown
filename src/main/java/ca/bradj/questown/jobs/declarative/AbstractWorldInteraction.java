package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.jobs.HeldItem;
import ca.bradj.questown.jobs.Item;
import ca.bradj.questown.jobs.WorkSpot;
import ca.bradj.questown.town.WorkStatusStore;
import ca.bradj.questown.town.interfaces.WorkStateContainer;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

public abstract class AbstractWorldInteraction<
        EXTRA, POS, INNER_ITEM extends Item<INNER_ITEM>, HELD_ITEM extends HeldItem<HELD_ITEM, INNER_ITEM>
> implements WorkStatusStore.InsertionRules<HELD_ITEM> {
    private final AbstractItemWI<POS, EXTRA, HELD_ITEM> itemWI;
    private int ticksSinceLastAction;
    private final int interval;
    protected final int maxState;

    protected final ImmutableMap<Integer, Function<INNER_ITEM, Boolean>> toolsRequiredAtStates;
    protected final ImmutableMap<Integer, Integer> workRequiredAtStates;
    private final ImmutableMap<Integer, Function<HELD_ITEM, Boolean>> ingredientsRequiredAtStates;

    private final Supplier<Collection<INNER_ITEM>> journalItems;

    public AbstractWorldInteraction(
            int interval,
            int maxState,
            ImmutableMap<Integer, Function<INNER_ITEM, Boolean>> toolsRequiredAtStates,
            ImmutableMap<Integer, Integer> workRequiredAtStates,
            ImmutableMap<Integer, Function<HELD_ITEM, Boolean>> ingredientsRequiredAtStates,
            ImmutableMap<Integer, Integer> ingredientQuantityRequiredAtStates,
            ImmutableMap<Integer, Integer> timeRequiredAtStates,
            Supplier<Collection<INNER_ITEM>> journalItems,
            InventoryHandle<HELD_ITEM> inventory
    ) {
        this.interval = interval;
        this.maxState = maxState;
        this.toolsRequiredAtStates = toolsRequiredAtStates;
        this.workRequiredAtStates = workRequiredAtStates;
        this.ingredientsRequiredAtStates = ingredientsRequiredAtStates;
        this.journalItems = journalItems;
        AbstractWorldInteraction<EXTRA, POS, INNER_ITEM, HELD_ITEM> self = this;
        this.itemWI = new AbstractItemWI<>(
                ingredientsRequiredAtStates,
                ingredientQuantityRequiredAtStates,
                workRequiredAtStates,
                timeRequiredAtStates,
                inventory
        ) {
            @Override
            protected WorkStateContainer<POS> getWorkStatuses(EXTRA extra) {
                return self.getWorkStatuses(extra);
            }

            @Override
            protected boolean canInsertItem(
                    EXTRA extra,
                    HELD_ITEM item,
                    POS bp
            ) {
                return self.canInsertItem(extra, item, bp);
            }
        };
    }

    protected abstract boolean canInsertItem(
            EXTRA extra,
            HELD_ITEM item,
            POS bp
    );

    protected abstract WorkStateContainer<POS> getWorkStatuses(EXTRA extra);

    public boolean tryWorking(
            EXTRA extra,
            WorkSpot<Integer, POS> workSpot
    ) {
        if (!isReady(extra)) {
            return false;
        }

        ticksSinceLastAction++;
        if (ticksSinceLastAction < interval) {
            return false;
        }
        ticksSinceLastAction = 0;

        if (workSpot == null) {
            return false;
        }

        if (!isEntityClose(extra, workSpot.position)) {
            return false;
        }

        WorkStateContainer<POS> workStatuses = getWorkStatuses(extra);
        WorkStatusStore.State jobBlockState = workStatuses.getJobBlockState(workSpot.position);

        if (workSpot.action == maxState) {
            if (jobBlockState != null && jobBlockState.workLeft() == 0) {
                return tryExtractOre(extra, workSpot.position);
            }
        }

        Function<INNER_ITEM, Boolean> tool = toolsRequiredAtStates.get(workSpot.action);
        if (tool != null) {
            Collection<INNER_ITEM> items = journalItems.get();
            boolean foundTool = items.stream().anyMatch(tool::apply);
            if (!foundTool) {
                return false;
            }
        }

        if (this.ingredientsRequiredAtStates.get(workSpot.action) != null) {
            if (itemWI.tryInsertIngredients(extra, workSpot)) {
                return true;
            }
        }

        if (this.workRequiredAtStates.containsKey(workSpot.action)) {
            Integer work = this.workRequiredAtStates.get(workSpot.action);
            if (work != null && work > 0) {
                if (workSpot.action == 0) {
                    if (jobBlockState == null) {
                        jobBlockState = new WorkStatusStore.State(0, 0, 0);
                    }
                    if (jobBlockState.workLeft() == 0) {
                        workStatuses.setJobBlockState(workSpot.position, jobBlockState.setWorkLeft(work));
                        return true;
                    }
                }
                return tryProcessOre(extra, workSpot);
            }
        }

        return false;
    }

    @Override
    public Map<Integer, Function<HELD_ITEM, Boolean>> ingredientsRequiredAtStates() {
        return ingredientsRequiredAtStates;
    }

    protected abstract boolean tryProcessOre(
            EXTRA extra,
            WorkSpot<Integer, POS> workSpot
    );

    protected abstract boolean tryExtractOre(
            EXTRA extra,
            POS position
    );

    protected abstract boolean isEntityClose(EXTRA extra, POS position);

    protected abstract boolean isReady(EXTRA extra);

    public @Nullable WorkStatusStore.State getJobBlockState(
            EXTRA extra,
            POS bp
    ) {
        return itemWI.getWorkStatuses(extra).getJobBlockState(bp);
    }
}
