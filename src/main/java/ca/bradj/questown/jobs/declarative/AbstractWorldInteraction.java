package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.jobs.HeldItem;
import ca.bradj.questown.jobs.Item;
import ca.bradj.questown.jobs.WorkSpot;
import ca.bradj.questown.town.WorkStatusStore;
import com.google.common.collect.ImmutableMap;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

public abstract class AbstractWorldInteraction<
        EXTRA, POS, INNER_ITEM extends Item<INNER_ITEM>, HELD_ITEM extends HeldItem<HELD_ITEM, INNER_ITEM>
> implements WorkStatusStore.InsertionRules<HELD_ITEM> {
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
            Supplier<Collection<INNER_ITEM>> journalItems
    ) {
        this.interval = interval;
        this.maxState = maxState;
        this.toolsRequiredAtStates = toolsRequiredAtStates;
        this.workRequiredAtStates = workRequiredAtStates;
        this.ingredientsRequiredAtStates = ingredientsRequiredAtStates;
        this.journalItems = journalItems;
    }

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

        if (workSpot.action == maxState) {
            return tryExtractOre(extra, workSpot.position);
        }

        Function<INNER_ITEM, Boolean> tool = toolsRequiredAtStates.get(workSpot.action);
        if (tool != null) {
            Collection<INNER_ITEM> items = journalItems.get();
            boolean foundTool = items.stream().anyMatch(tool::apply);
            if (!foundTool) {
                return false;
            }
        }

        if (this.workRequiredAtStates.containsKey(workSpot.action)) {
            Integer work = this.workRequiredAtStates.get(workSpot.action);
            if (work != null && work > 0) {
                if (workSpot.action == 0) {
                    WorkStatusStore.State jobBlockState = getJobBlockState(extra, workSpot.position);
                    if (jobBlockState == null) {
                        jobBlockState = new WorkStatusStore.State(0, 0, 0);
                    }
                    if (jobBlockState.workLeft() == 0) {
                        setJobBlockState(extra, workSpot.position, jobBlockState.setWorkLeft(work));
                    }
                }
                if (this.ingredientsRequiredAtStates.get(workSpot.action) != null) {
                    this.getItemWI().tryInsertIngredients(extra, workSpot);
                }
                return tryProcessOre(extra, workSpot);
            }
        }

        if (this.ingredientsRequiredAtStates.get(workSpot.action) != null) {
            return this.getItemWI().tryInsertIngredients(extra, workSpot);
        }

        return false;
    }

    protected abstract ItemWI<POS,EXTRA> getItemWI();

    @Override
    public Map<Integer, Function<HELD_ITEM, Boolean>> ingredientsRequiredAtStates() {
        return ingredientsRequiredAtStates;
    }

    protected abstract boolean tryProcessOre(
            EXTRA extra,
            WorkSpot<Integer, POS> workSpot
    );

    protected abstract void setJobBlockState(
            EXTRA extra,
            POS position,
            WorkStatusStore.State state
    );

    protected abstract WorkStatusStore.State getJobBlockState(
            EXTRA extra,
            POS position
    );

    protected abstract boolean tryExtractOre(
            EXTRA extra,
            POS position
    );

    protected abstract boolean isEntityClose(EXTRA extra, POS position);

    protected abstract boolean isReady(EXTRA extra);

}
