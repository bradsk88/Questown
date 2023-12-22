package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.QT;
import ca.bradj.questown.jobs.HeldItem;
import ca.bradj.questown.jobs.Item;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.WorkSpot;
import ca.bradj.questown.town.AbstractWorkStatusStore;
import ca.bradj.questown.town.interfaces.ImmutableWorkStateContainer;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;

public abstract class AbstractWorldInteraction<
        EXTRA, POS, INNER_ITEM extends Item<INNER_ITEM>, HELD_ITEM extends HeldItem<HELD_ITEM, INNER_ITEM>,
        TOWN
> implements AbstractWorkStatusStore.InsertionRules<HELD_ITEM> {
    private final AbstractItemWI<POS, EXTRA, HELD_ITEM, TOWN> itemWI;
    private final AbstractWorkWI<POS, EXTRA, INNER_ITEM, TOWN> workWI;
    protected final int villagerIndex;
    protected int ticksSinceLastAction;
    public final int interval;
    protected final int maxState;

    protected final ImmutableMap<Integer, Function<INNER_ITEM, Boolean>> toolsRequiredAtStates;
    protected final ImmutableMap<Integer, Integer> workRequiredAtStates;
    private final ImmutableMap<Integer, Function<HELD_ITEM, Boolean>> ingredientsRequiredAtStates;


    public AbstractWorldInteraction(
            JobID jobId,
            int villagerIndex,
            int interval,
            int maxState,
            ImmutableMap<Integer, Function<INNER_ITEM, Boolean>> toolsRequiredAtStates,
            ImmutableMap<Integer, Integer> workRequiredAtStates,
            ImmutableMap<Integer, Function<HELD_ITEM, Boolean>> ingredientsRequiredAtStates,
            ImmutableMap<Integer, Integer> ingredientQuantityRequiredAtStates,
            ImmutableMap<Integer, Integer> timeRequiredAtStates
    ) {
        if (toolsRequiredAtStates.isEmpty() && workRequiredAtStates.isEmpty() && ingredientQuantityRequiredAtStates.isEmpty() && timeRequiredAtStates.isEmpty()) {
            QT.JOB_LOGGER.error("{} requires no tools, work, time, or ingredients. This will lead to strange game behaviour.", jobId);
        }
        this.villagerIndex = villagerIndex;
        this.interval = interval;
        this.maxState = maxState;
        this.toolsRequiredAtStates = toolsRequiredAtStates;
        this.workRequiredAtStates = workRequiredAtStates;
        this.ingredientsRequiredAtStates = ingredientsRequiredAtStates;
        AbstractWorldInteraction<EXTRA, POS, INNER_ITEM, HELD_ITEM, TOWN> self = this;
        this.itemWI = new AbstractItemWI<>(
                villagerIndex,
                ingredientsRequiredAtStates,
                ingredientQuantityRequiredAtStates,
                workRequiredAtStates,
                timeRequiredAtStates
        ) {
            @Override
            protected TOWN setHeldItem(EXTRA uxtra, TOWN tuwn, int villagerIndex, int itemIndex, HELD_ITEM item) {
                return self.setHeldItem(uxtra, tuwn, villagerIndex, itemIndex, item);
            }

            @Override
            protected Collection<HELD_ITEM> getHeldItems(EXTRA extra, int villagerIndex) {
                return self.getHeldItems(extra, villagerIndex);
            }

            @Override
            protected ImmutableWorkStateContainer<POS, TOWN> getWorkStatuses(EXTRA extra) {
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
        this.workWI = new AbstractWorkWI<>(
                workRequiredAtStates,
                timeRequiredAtStates,
                toolsRequiredAtStates
        ) {
            @Override
            protected TOWN degradeTool(
                    EXTRA extra,
                    @Nullable TOWN tuwn,
                    Function<INNER_ITEM, Boolean> heldItemBooleanFunction
            ) {
                return self.degradeTool(extra, tuwn, heldItemBooleanFunction); // TODO: Implement generically and test
            }

            @Override
            protected ImmutableWorkStateContainer<POS, TOWN> getWorkStatuses(EXTRA extra) {
                return self.getWorkStatuses(extra);
            }
        };
    }

    protected abstract TOWN setHeldItem(EXTRA uxtra, TOWN tuwn, int villagerIndex, int itemIndex, HELD_ITEM item);

    protected abstract TOWN degradeTool(
            EXTRA extra,
            @Nullable TOWN tuwn,
            Function<INNER_ITEM, Boolean> heldItemBooleanFunction
    );

    protected abstract boolean canInsertItem(
            EXTRA extra,
            HELD_ITEM item,
            POS bp
    );

    protected abstract ImmutableWorkStateContainer<POS, TOWN> getWorkStatuses(EXTRA extra);

    public TOWN tryWorking(
            EXTRA extra,
            WorkSpot<Integer, POS> workSpot
    ) {
        if (!isReady(extra)) {
            return null;
        }

        ticksSinceLastAction++;
        if (ticksSinceLastAction < interval) {
            return null;
        }
        ticksSinceLastAction = 0;

        if (workSpot == null) {
            return null;
        }

        if (!isEntityClose(extra, workSpot.position())) {
            return null;
        }

        ImmutableWorkStateContainer<POS, TOWN> workStatuses = getWorkStatuses(extra);
        AbstractWorkStatusStore.State jobBlockState = workStatuses.getJobBlockState(workSpot.position());

        if (workSpot.action() == maxState) {
            if (jobBlockState != null && jobBlockState.workLeft() == 0) {
                return tryExtractOre(extra, workSpot.position());
            }
        }

        Function<INNER_ITEM, Boolean> tool = toolsRequiredAtStates.get(workSpot.action());
        if (tool != null) {
            Collection<HELD_ITEM> items = getHeldItems(extra, villagerIndex);
            boolean foundTool = items.stream().anyMatch(i -> tool.apply(i.get()));
            if (!foundTool) {
                return null;
            }
        }

        if (this.ingredientsRequiredAtStates.get(workSpot.action()) != null) {
            TOWN o = itemWI.tryInsertIngredients(extra, workSpot);
            if (o != null) {
                return o;
            }
        }

        if (this.workRequiredAtStates.containsKey(workSpot.action())) {
            Integer work = this.workRequiredAtStates.get(workSpot.action());
            if (work != null && work > 0) {
                if (workSpot.action() == 0) {
                    if (jobBlockState == null) {
                        jobBlockState = new AbstractWorkStatusStore.State(0, 0, 0);
                    }
                    if (jobBlockState.workLeft() == 0) {
                        return workStatuses.setJobBlockState(workSpot.position(), jobBlockState.setWorkLeft(work));
                    }
                }
            }
        }

        // TODO: If workspot is waiting for time, return  null

        return workWI.tryWork(extra, workSpot);
    }

    protected abstract Collection<HELD_ITEM> getHeldItems(EXTRA extra, int villagerIndex);

    @Override
    public Map<Integer, Function<HELD_ITEM, Boolean>> ingredientsRequiredAtStates() {
        return ingredientsRequiredAtStates;
    }

    protected abstract TOWN tryExtractOre(
            EXTRA extra,
            POS position
    );

    protected abstract boolean isEntityClose(EXTRA extra, POS position);

    protected abstract boolean isReady(EXTRA extra);

    public @Nullable AbstractWorkStatusStore.State getJobBlockState(
            EXTRA extra,
            POS bp
    ) {
        return itemWI.getWorkStatuses(extra).getJobBlockState(bp);
    }
}
