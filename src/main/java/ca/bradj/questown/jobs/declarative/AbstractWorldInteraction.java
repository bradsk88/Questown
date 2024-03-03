package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.QT;
import ca.bradj.questown.items.EffectMetaItem;
import ca.bradj.questown.items.KnowledgeMetaItem;
import ca.bradj.questown.jobs.*;
import ca.bradj.questown.town.AbstractWorkStatusStore;
import ca.bradj.questown.town.Claim;
import ca.bradj.questown.town.interfaces.ImmutableWorkStateContainer;
import com.google.common.collect.ImmutableMap;
import org.apache.logging.log4j.util.TriConsumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.Stack;
import java.util.function.Function;

public abstract class AbstractWorldInteraction<
        EXTRA, POS, INNER_ITEM extends Item<INNER_ITEM>, HELD_ITEM extends HeldItem<HELD_ITEM, INNER_ITEM>,
        TOWN
        > implements AbstractWorkStatusStore.InsertionRules<HELD_ITEM> {
    private final AbstractItemWI<POS, EXTRA, HELD_ITEM, TOWN> itemWI;
    private final AbstractWorkWI<POS, EXTRA, INNER_ITEM, TOWN> workWI;
    protected final int villagerIndex;
    private final Function<EXTRA, Claim> claimSpots;
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
            ImmutableMap<Integer, Integer> timeRequiredAtStates,
            Function<EXTRA, Claim> claimSpots
    ) {
        if (toolsRequiredAtStates.isEmpty() && workRequiredAtStates.isEmpty() && ingredientQuantityRequiredAtStates.isEmpty() && timeRequiredAtStates.isEmpty()) {
            QT.JOB_LOGGER.error(
                    "{} requires no tools, work, time, or ingredients. This will lead to strange game behaviour.",
                    jobId
            );
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
                timeRequiredAtStates,
                claimSpots
        ) {
            @Override
            protected TOWN setHeldItem(
                    EXTRA uxtra,
                    TOWN tuwn,
                    int villagerIndex,
                    int itemIndex,
                    HELD_ITEM item
            ) {
                return self.setHeldItem(uxtra, tuwn, villagerIndex, itemIndex, item);
            }

            @Override
            protected Collection<HELD_ITEM> getHeldItems(
                    EXTRA extra,
                    int villagerIndex
            ) {
                return self.getHeldItems(extra, villagerIndex);
            }

            @Override
            protected ImmutableWorkStateContainer<POS, TOWN> getWorkStatuses(
                    EXTRA extra
            ) {
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
            protected Integer getAugmentedTime(EXTRA extra, Integer nextStepTime) {
                return self.getAugmentedTime(extra, nextStepTime);
            }

            @Override
            protected TOWN degradeTool(
                    EXTRA extra,
                    @Nullable TOWN tuwn,
                    Function<INNER_ITEM, Boolean> heldItemBooleanFunction
            ) {
                return self.degradeTool(extra, tuwn, heldItemBooleanFunction); // TODO: Implement generically and test
            }

            @Override
            protected ImmutableWorkStateContainer<POS, TOWN> getWorkStatuses(
                    EXTRA extra
            ) {
                return self.getWorkStatuses(extra);
            }
        };
        this.claimSpots = claimSpots;
    }

    protected abstract int getAugmentedTime(EXTRA extra, Integer nextStepTime);

    protected abstract TOWN setHeldItem(
            EXTRA uxtra,
            TOWN tuwn,
            int villagerIndex,
            int itemIndex,
            HELD_ITEM item
    );

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

    protected abstract ImmutableWorkStateContainer<POS, TOWN> getWorkStatuses(
            EXTRA extra
    );

    public @Nullable WorkOutput<@Nullable TOWN, WorkSpot<Integer, POS>> tryWorking(
            EXTRA extra,
            WorkSpot<Integer, POS> workSpot
    ) {
        if (!isReady(extra)) {
            return null;
        }
        boolean canClaim = getWorkStatuses(extra).canClaim(workSpot.position(), () -> this.claimSpots.apply(extra));

        ticksSinceLastAction++;
        if (ticksSinceLastAction < interval) {
            if (canClaim) {
                return new WorkOutput<>(null, workSpot);
            }
            return null;
        }
        ticksSinceLastAction = 0;

        if (!canClaim) {
            return null;
        }

        WorkOutput<TOWN, WorkSpot<Integer, POS>> vNull = new WorkOutput<>(null, workSpot);

        if (!isEntityClose(extra, workSpot.position())) {
            return vNull;
        }

        ImmutableWorkStateContainer<POS, TOWN> workStatuses = getWorkStatuses(extra);
        AbstractWorkStatusStore.State jobBlockState = workStatuses.getJobBlockState(workSpot.position());

        if (workSpot.action() == maxState) {
            if (jobBlockState != null && jobBlockState.workLeft() == 0) {
                return new WorkOutput<>(tryExtractOre(extra, workSpot.position()), workSpot);
            }
        }

        Function<INNER_ITEM, Boolean> tool = toolsRequiredAtStates.get(workSpot.action());
        if (tool != null) {
            Collection<HELD_ITEM> items = getHeldItems(extra, villagerIndex);
            boolean foundTool = items.stream().anyMatch(i -> tool.apply(i.get()));
            if (!foundTool) {
                return vNull;
            }
        }

        if (this.ingredientsRequiredAtStates.get(workSpot.action()) != null) {
            TOWN o = itemWI.tryInsertIngredients(extra, workSpot);
            if (o != null) {
                return new WorkOutput<>(o, workSpot);
            }
        }

        if (this.workRequiredAtStates.containsKey(workSpot.action())) {
            Integer work = this.workRequiredAtStates.get(workSpot.action());
            if (work != null && work > 0) {
                if (workSpot.action() == 0) {
                    if (jobBlockState == null) {
                        jobBlockState = AbstractWorkStatusStore.State.fresh();
                    }
                    if (jobBlockState.workLeft() == 0) {
                        TOWN town = workStatuses.setJobBlockState(workSpot.position(), jobBlockState.setWorkLeft(work));
                        return new WorkOutput<>(town, workSpot);
                    }
                }
            }
        }

        // TODO: If workspot is waiting for time, return  null

        return new WorkOutput<>(
                workWI.tryWork(extra, workSpot),
                workSpot
        );
    }

    protected abstract Collection<HELD_ITEM> getHeldItems(
            EXTRA extra,
            int villagerIndex
    );

    @Override
    public Map<Integer, Function<HELD_ITEM, Boolean>> ingredientsRequiredAtStates() {
        return ingredientsRequiredAtStates;
    }

    protected TOWN tryExtractOre(
            @NotNull EXTRA inputs,
            POS position
    ) {
        AbstractWorkStatusStore.State s = getJobBlockState(inputs, position);
        if (s != null && s.processingState() == maxState) {

            Collection<HELD_ITEM> items = getHeldItems(inputs, villagerIndex);
            Iterable<HELD_ITEM> generatedResult = getResults(inputs, items);

            Stack<HELD_ITEM> stack = new Stack<>();
            generatedResult.forEach(stack::push);

            TOWN ts = getTown(inputs);
            if (stack.isEmpty()) {
                QT.JOB_LOGGER.error(
                        "No results during extraction phase. That's probably a bug. Town State: {}",
                        ts
                );
                return ts;
            }

            int i = -1;
            for (HELD_ITEM item : items) {
                i++;
                if (!item.isEmpty()) {
                    continue;
                }
                HELD_ITEM newItem = stack.pop();
                if (isMulti(newItem.get())) {
                    stack.push(newItem.shrink());
                }
                if (isInstanze(newItem.get(), KnowledgeMetaItem.class)) {
                    ts = withKnowledge(inputs, ts, newItem);
                } else if (isInstanze(newItem.get(), EffectMetaItem.class)) {
                    ts = withEffectApplied(inputs, ts, newItem);
                } else {
                    ts = setHeldItem(inputs, ts, villagerIndex, i, newItem.unit());
                }
                ts = setJobBlockState(inputs, ts, position, AbstractWorkStatusStore.State.fresh());
                getWorkStatuses(inputs).clearClaim(position);

                if (stack.isEmpty()) {
                    return ts;
                }
            }
            // TODO: If SpecialRules.NULLIFY_EXCESS_RESULTS does not apply, should we spawn items in town?
        }
        return null;
    }

    protected abstract TOWN setJobBlockState(
            @NotNull EXTRA inputs,
            TOWN ts,
            POS position,
            AbstractWorkStatusStore.State fresh
    );

    protected abstract TOWN withEffectApplied(
            @NotNull EXTRA inputs,
            TOWN ts,
            HELD_ITEM newItem
    );

    protected abstract TOWN withKnowledge(
            @NotNull EXTRA inputs,
            TOWN ts,
            HELD_ITEM newItem
    );

    protected abstract boolean isInstanze(
            INNER_ITEM innerItem,
            Class<?> clazz
    );

    protected abstract boolean isMulti(INNER_ITEM innerItem);

    protected abstract TOWN getTown(EXTRA inputs);

    protected abstract Iterable<HELD_ITEM> getResults(
            EXTRA inputs,
            Collection<HELD_ITEM> items
    );

    protected abstract boolean isEntityClose(
            EXTRA extra,
            POS position
    );

    protected abstract boolean isReady(EXTRA extra);

    public @Nullable AbstractWorkStatusStore.State getJobBlockState(
            EXTRA extra,
            POS bp
    ) {
        return itemWI.getWorkStatuses(extra).getJobBlockState(bp);
    }

    protected void addItemInsertionListener(TriConsumer<EXTRA, POS, HELD_ITEM> listener) {
        this.itemWI.addItemInsertionListener(listener);
    };
}
