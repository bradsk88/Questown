package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.QT;
import ca.bradj.questown.items.EffectMetaItem;
import ca.bradj.questown.items.KnowledgeMetaItem;
import ca.bradj.questown.jobs.*;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.mc.Util;
import ca.bradj.questown.town.AbstractWorkStatusStore;
import ca.bradj.questown.town.Claim;
import ca.bradj.questown.town.interfaces.ImmutableWorkStateContainer;
import ca.bradj.questown.town.workstatus.State;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.apache.logging.log4j.util.TriConsumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
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

    private final List<Runnable> jobCompletedListeners = new ArrayList<>();

    protected final ImmutableMap<Integer, Function<INNER_ITEM, Boolean>> toolsRequiredAtStates;
    protected final ImmutableMap<Integer, Integer> workRequiredAtStates;
    private final ImmutableMap<Integer, Function<HELD_ITEM, Boolean>> ingredientsRequiredAtStates;
    private WithReason<@Nullable WorkPosition<POS>> workspot = new WithReason<>(null, "Never set");

    private final ImmutableMap<ProductionStatus, Collection<String>> specialRules;


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
            Function<EXTRA, Claim> claimSpots,
            Map<ProductionStatus, Collection<String>> specialRules
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
        this.specialRules = ImmutableMap.copyOf(specialRules);

        AbstractWorldInteraction<EXTRA, POS, INNER_ITEM, HELD_ITEM, TOWN> self = this;
        this.itemWI = new AbstractItemWI<>(
                villagerIndex,
                ingredientsRequiredAtStates,
                ingredientQuantityRequiredAtStates,
                workRequiredAtStates,
                (x, s) -> getAffectedTime(x, timeRequiredAtStates.getOrDefault(s, 0)),
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
                (x, s) -> getAffectedTime(x, timeRequiredAtStates.getOrDefault(s, 0)),
                toolsRequiredAtStates,
                this::preStateChangeHooks
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
            protected TOWN setJobBlockStateWithTimer(EXTRA extra, POS bp, State bs, int nextStepTime) {
                return getWorkStatuses(extra).setJobBlockStateWithTimer(bp, bs, nextStepTime);
            }

            @Override
            protected TOWN setJobBlockState(EXTRA extra, POS bp, State bs) {
                return getWorkStatuses(extra).setJobBlockState(bp, bs);
            }

            @Override
            protected State getJobBlockState(EXTRA extra, POS bp) {
                return self.getWorkStatuses(extra).getJobBlockState(bp);
            }

            @Override
            protected int getWorkSpeedOf10(EXTRA extra) {
                return self.getWorkSpeedOf10(extra);
            }
        };
        this.claimSpots = claimSpots;
    }

    protected TOWN tryGiveItems(
            EXTRA inputs,
            Iterable<HELD_ITEM> newItemsSource,
            POS sourcePos
    ) {
        Function<TOWN, TOWN> reset = getResetFunc(inputs, sourcePos);

        Stack<HELD_ITEM> stack = new Stack<>();

        Util.iterate(newItemsSource, stack::push);

        TOWN ts = getTown(inputs);
        if (stack.isEmpty()) {
            QT.JOB_LOGGER.error(
                    "No results during extraction phase. That's probably a bug. Town State: {}",
                    ts
            );
            return reset.apply(ts);
        }

        boolean gotAll = false;
        int i = -1;
        for (HELD_ITEM item : getHeldItems(inputs, villagerIndex)) {
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
                HELD_ITEM unit = newItem.unit();
                ts = setHeldItem(inputs, ts, villagerIndex, i, unit);
                QT.VILLAGER_LOGGER.debug("Villager took {}", unit.toShortString());
            }

            if (stack.isEmpty()) {
                gotAll = true;
                break;
            }
        }
        if (!gotAll) {
            // TODO: Gracefully handle when the villager doesn't have enough room to take all items
            QT.VILLAGER_LOGGER.debug("Villager ran out of room before extracting all possible items");
        }
        return reset.apply(ts);
    }

    private @NotNull Function<TOWN, TOWN> getResetFunc(
            EXTRA inputs,
            POS workSpot
    ) {
        Function<TOWN, TOWN> reset = (TOWN ts) -> {
            ts = setJobBlockState(inputs, ts, workSpot, State.fresh());
            getWorkStatuses(inputs).clearClaim(workSpot);
            return ts;
        };
        return reset;
    }

    protected abstract int getWorkSpeedOf10(EXTRA extra);

    protected abstract int getAffectedTime(
            EXTRA extra,
            Integer nextStepTime
    );

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


    public WorkOutput<TOWN, WorkPosition<POS>> tryWorking(
            EXTRA extra,
            Preferred<WorkPosition<POS>> workSpots
    ) {
        ArrayList<WorkPosition<POS>> shuffled = shuffle(extra, workSpots.alternates());
        if (workSpots.preferredValue() != null) {
            shuffled.removeIf(v -> v.equals(workSpots.preferredValue()));
            shuffled.add(0, workSpots.preferredValue());
        }
        for (WorkPosition<POS> workSpot : shuffled) {
            WorkOutput<TOWN, WorkPosition<POS>> v = tryWorking(extra, workSpot);
            if (v != null && v.worked()) {
                return v;
            }
        }
        return new WorkOutput<>(
                false,
                null,
                ImmutableList.copyOf(shuffled).get(0)
        );
    }

    protected abstract ArrayList<WorkPosition<POS>> shuffle(
            EXTRA extra,
            Collection<WorkPosition<POS>> workSpots
    );

    public @Nullable WorkOutput<@Nullable TOWN, WorkPosition<POS>> tryWorking(
            EXTRA extra,
            WorkPosition<POS> workSpot
    ) {
        if (!isReady(extra)) {
            return null;
        }
        boolean canClaim = getWorkStatuses(extra).canClaim(workSpot.jobBlock(), () -> this.claimSpots.apply(extra));

        ticksSinceLastAction++;
        if (ticksSinceLastAction < interval) {
            if (canClaim) {
                return new WorkOutput<>(false, null, workSpot);
            }
            return null;
        }
        ticksSinceLastAction = 0;

        if (!canClaim) {
            return null;
        }

        WorkOutput<TOWN, WorkPosition<POS>> vNull = new WorkOutput<>(false, null, workSpot);

        if (!isEntityClose(extra, workSpot.jobBlock())) {
            return vNull;
        }

        ImmutableWorkStateContainer<POS, TOWN> workStatuses = getWorkStatuses(extra);
        State jobBlockState = workStatuses.getJobBlockState(workSpot.jobBlock());

        int action = Util.withNullFallback(jobBlockState, State::processingState, 0);
        if (action >= maxState) {
            if (jobBlockState != null && jobBlockState.workLeft() == 0) {
                TOWN ex = tryExtractProduct(extra, workSpot.jobBlock());
                return new WorkOutput<>(true, ex, workSpot);
            }
        }

        Function<INNER_ITEM, Boolean> tool = toolsRequiredAtStates.get(action);
        if (tool != null) {
            Collection<HELD_ITEM> items = getHeldItems(extra, villagerIndex);
            boolean foundTool = items.stream().anyMatch(i -> tool.apply(i.get()));
            if (!foundTool) {
                return vNull;
            }
        }

        TOWN initTown = getTown(extra);
        if (this.ingredientsRequiredAtStates.get(action) != null) {
            InsertResult<TOWN, HELD_ITEM> o = itemWI.tryInsertIngredients(extra, getCurWorkedSpot(extra, initTown, workSpot.jobBlock()));
            if (o != null) {
                TOWN ctx = o.contextAfterInsert();
                HELD_ITEM item = o.itemBeforeInsert();
                @Nullable TOWN out = postInsertHook(ctx, extra, getCurWorkedSpot(extra, ctx, workSpot.jobBlock()), item);
                if (out == null) {
                    out = ctx;
                }
                return new WorkOutput<>(true, out, workSpot);
            }
        }

        if (this.workRequiredAtStates.containsKey(action)) {
            Integer work = this.workRequiredAtStates.get(action);
            if (work != null && work > 0) {
                if (action == 0) {
                    if (jobBlockState == null) {
                        jobBlockState = State.fresh();
                    }
                    if (jobBlockState.workLeft() == 0) {
                        TOWN town = workStatuses.setJobBlockState(workSpot.jobBlock(), jobBlockState.setWorkLeft(work));
                        return new WorkOutput<>(false, town, workSpot);
                    }
                }
            }
        }

        // TODO: If workspot is waiting for time, return  null

        TOWN town = workWI.tryWork(extra, getCurWorkedSpot(extra, initTown, workSpot.jobBlock()));
        return new WorkOutput<>(town != null, town, workSpot);
    }

    protected abstract WorkedSpot<POS> getCurWorkedSpot(EXTRA extra, TOWN stateSource, POS workSpot);

    protected abstract Collection<HELD_ITEM> getHeldItems(
            EXTRA extra,
            int villagerIndex
    );

    @Override
    public Map<Integer, Function<HELD_ITEM, Boolean>> ingredientsRequiredAtStates() {
        return ingredientsRequiredAtStates;
    }

    protected TOWN tryExtractProduct(
            @NotNull EXTRA inputs,
            POS position
    ) {
        State s = getJobBlockState(inputs, position);
        if (s != null && s.processingState() >= maxState) {

            TOWN town = preExtractHook(inputs, position);
            if (town != null) {
                Function<TOWN, TOWN> resetFunc = getResetFunc(inputs, position);
                town = resetFunc.apply(town);
            }
            if (town == null) {
                Collection<HELD_ITEM> items = getHeldItems(inputs, villagerIndex);
                Iterable<HELD_ITEM> generatedResult = getResults(inputs, items);

                town = tryGiveItems(inputs, generatedResult, position);
            }
            if (town != null) {
                jobCompletedListeners.forEach(Runnable::run);
            }
            return town;
            // TODO: If SpecialRules.NULLIFY_EXCESS_RESULTS does not apply, should we spawn items in town?
        }
        return null;
    }

    private void preStateChangeHooks(
            EXTRA inputs,
            WorkSpot<Integer, POS> position
    ) {
        Collection<String> rules = specialRules.get(ProductionStatus.fromJobBlockStatus(position.action()));
        if (rules == null || rules.isEmpty()) {
            return;
        }
        preStateChangeHooks(getTown(inputs), rules, inputs, position);
    }

    protected abstract void preStateChangeHooks(
            @NotNull TOWN ctx,
            Collection<String> rules,
            EXTRA inputs,
            WorkSpot<Integer, POS> position
    );

    private @Nullable TOWN postInsertHook(
            @NotNull TOWN ctx,
            EXTRA inputs,
            WorkedSpot<POS> position,
            HELD_ITEM item
    ) {
        Collection<String> rules = specialRules.get(ProductionStatus.fromJobBlockStatus(position.stateAfterWork()));
        if (rules == null || rules.isEmpty()) {
            return ctx;
        }
        return postInsertHook(getTown(inputs), rules, inputs, position, item);
    }

    protected abstract @Nullable TOWN postInsertHook(
            @NotNull TOWN town,
            Collection<String> rules,
            EXTRA inputs,
            WorkedSpot<POS> position,
            HELD_ITEM item
    );

    private @Nullable TOWN preExtractHook(
            EXTRA inputs,
            POS position
    ) {
        Collection<String> rules = specialRules.get(ProductionStatus.EXTRACTING_PRODUCT);
        if (rules == null || rules.isEmpty()) {
            return null;
        }
        return preExtractHook(getTown(inputs), rules, inputs, position);
    }

    protected abstract @Nullable TOWN preExtractHook(
            TOWN town,
            Collection<String> rules,
            EXTRA inputs,
            POS position
    );

    protected abstract TOWN setJobBlockState(
            @NotNull EXTRA inputs,
            TOWN ts,
            POS position,
            State fresh
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

    @Nullable
    State getJobBlockState(
            EXTRA extra,
            POS bp
    ) {
        return itemWI.getWorkStatuses(extra).getJobBlockState(bp);
    }

    public void addItemInsertionListener(TriConsumer<EXTRA, POS, HELD_ITEM> listener) {
        this.itemWI.addItemInsertionListener(listener);
    }

    ;

    public void removeItemInsertionListener(TriConsumer<EXTRA, POS, HELD_ITEM> listener) {
        this.itemWI.removeItemInsertionListener(listener);
    }

    ;

    public void addJobCompletionListener(Runnable listener) {
        this.jobCompletedListeners.add(listener);
    }

    ;

    public void removeJobCompletionListener(Runnable listener) {
        this.jobCompletedListeners.remove(listener);
    }

    ;

    public abstract boolean tryGrabbingInsertedSupplies(
            EXTRA mcExtra
    );

    public @Nullable WorkPosition<POS> getWorkSpot() {
        return workspot.value;
    }

    public void setWorkSpot(WithReason<@Nullable WorkPosition<POS>> o) {
        this.workspot = o;
    }
}
