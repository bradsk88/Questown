package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.QT;
import ca.bradj.questown.items.EffectMetaItem;
import ca.bradj.questown.items.KnowledgeMetaItem;
import ca.bradj.questown.jobs.*;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.logic.PredicateCollection;
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
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class AbstractWorldInteraction<
        EXTRA, POS, INNER_ITEM extends Item<INNER_ITEM>, HELD_ITEM extends HeldItem<HELD_ITEM, INNER_ITEM>,
        TOWN
        > implements AbstractWorkStatusStore.InsertionRules<HELD_ITEM> {
    private final AbstractItemWI<POS, EXTRA, HELD_ITEM, TOWN> itemWI;
    private final AbstractWorkWI<POS, EXTRA, INNER_ITEM, TOWN> workWI;
    private final NeedsRegistrations<POS, EXTRA> needsReg;
    protected final int villagerIndex;
    private final Function<EXTRA, Claim> claimSpots;
    protected final DeclarativeJobChecks<EXTRA, HELD_ITEM, INNER_ITEM, ?, POS> checks;
    private final JobID jobId;
    protected int ticksSinceLastAction;
    public final int interval;
    protected final int maxState;

    private final List<Consumer<JobID>> jobCompletedListeners = new ArrayList<>();

    private WithReason<@Nullable WorkPosition<POS>> workspot = new WithReason<>(null, "Never set");

    private final ImmutableMap<ProductionStatus, Collection<String>> specialRules;
    private final List<BiConsumer<EXTRA, POS>> extractionListeners = new ArrayList<>();


    public <S, T> AbstractWorldInteraction(
            JobID jobId,
            int villagerIndex,
            int interval,
            int maxState,
            DeclarativeJobChecks<EXTRA, HELD_ITEM, INNER_ITEM, ?, POS> checks,
            Function<EXTRA, Claim> claimSpots,
            Map<ProductionStatus, Collection<String>> specialRules
    ) {
        this.jobId = jobId;
        if (checks.isInsufficient()) {
            QT.JOB_LOGGER.error(
                    "{} requires no tools, work, time, or ingredients. This will lead to strange game behaviour.",
                    jobId
            );
        }
        this.needsReg = new NeedsRegistrations<>(
                this::registerUnmetRoom,
                this::registerUnmetNeed,
                this::getJobBlockState
        );
        this.checks = checks;
        this.villagerIndex = villagerIndex;
        this.interval = interval;
        this.maxState = maxState;
        this.specialRules = ImmutableMap.copyOf(specialRules);

        AbstractWorldInteraction<EXTRA, POS, INNER_ITEM, HELD_ITEM, TOWN> self = this;
        ItemWorkChecks<EXTRA, HELD_ITEM, INNER_ITEM> itemChecks = asItemChecks();
        this.itemWI = new AbstractItemWI<>(villagerIndex, itemChecks, claimSpots) {
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
            protected boolean isWorkSpotReadyForItem(
                    EXTRA extra,
                    HELD_ITEM item,
                    POS bp
            ) {
                return self.isWorkSpotReadyForItem(extra, item, bp);
            }
        };

        this.workWI = new AbstractWorkWI<>(
                itemChecks,
                this::preStateChangeHooks
        ) {
            @Override
            protected TOWN degradeTool(
                    EXTRA extra,
                    @Nullable TOWN tuwn,
                    PredicateCollection<INNER_ITEM, ?> heldItemBooleanFunction
            ) {
                return self.degradeTool(extra, tuwn, heldItemBooleanFunction); // TODO: Implement generically and test
            }

            @Override
            protected TOWN setJobBlockStateWithTimer(
                    EXTRA extra,
                    POS bp,
                    State bs,
                    int nextStepTime
            ) {
                return getWorkStatuses(extra).setJobBlockStateWithTimer(bp, bs, nextStepTime);
            }

            @Override
            protected TOWN setJobBlockState(
                    EXTRA extra,
                    POS bp,
                    State bs
            ) {
                return getWorkStatuses(extra).setJobBlockState(bp, bs);
            }

            @Override
            protected State getJobBlockState(
                    EXTRA extra,
                    POS bp
            ) {
                return self.getWorkStatuses(extra).getJobBlockState(bp);
            }

            @Override
            protected int getWorkSpeedOf10(EXTRA extra) {
                return self.getWorkSpeedOf10(extra);
            }
        };
        this.claimSpots = claimSpots;
    }

    private ItemWorkChecks<EXTRA, HELD_ITEM, INNER_ITEM> asItemChecks() {
        return new ItemWorkChecks<>() {
            @Override
            public boolean isWorkRequiredAtStep(int action) {
                return checks.isWorkRequiredAtStep(action);
            }

            @Override
            public @Nullable Integer getQuantityForStep(
                    int i,
                    @Nullable Integer orDefault
            ) {
                return checks.getQuantityForStep(i, orDefault);
            }

            @Override
            public @Nullable PredicateCollection<HELD_ITEM, HELD_ITEM> getIngredientsForStep(int i) {
                return checks.getIngredientsForStep(i);
            }

            @Override
            public int getWorkForStep(
                    int stepState,
                    int orDefault
            ) {
                return checks.getWorkForStep(stepState, orDefault);
            }

            @Override
            public @Nullable Integer getWorkForStep(int stepState) {
                return checks.getWorkForStep(stepState);
            }

            @Override
            public @Nullable Integer getTimeForStep(
                    EXTRA extra,
                    int stepState
            ) {
                Integer timeForStep = checks.getTimeForStep(extra, stepState);
                if (timeForStep == null) {
                    return null;
                }
                return getAffectedTime(extra, timeForStep);
            }

            @Override
            public int getTimeForStep(
                    EXTRA extra,
                    int stepState,
                    int orDefault
            ) {
                @Nullable Integer v = getTimeForStep(extra, stepState);
                return v == null ? 0 : v;
            }

            @Override
            public PredicateCollection<INNER_ITEM, ?> getToolsForStep(Integer curState) {
                return checks.getToolsForStep(curState);
            }
        };
    }

    protected TOWN tryGiveItems(
            EXTRA inputs,
            Iterable<HELD_ITEM> newItemsSource,
            POS sourcePos
    ) {
        Function<TOWN, TOWN> reset = getResetFunc(inputs, sourcePos);

        Stack<HELD_ITEM> stack = new Stack<>();

        iterate(newItemsSource, stack::push);

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
            if (isStacked(newItem.get())) {
                stack.push(newItem.shrink());
            }
            if (isInstanze(newItem.get(), KnowledgeMetaItem.class)) {
                ts = withKnowledge(inputs, ts, newItem);
            } else if (isInstanze(newItem.get(), EffectMetaItem.class)) {
                ts = withEffectApplied(inputs, ts, newItem);
            } else {
                HELD_ITEM unit = newItem.unit();
                TOWN hookResult = postExtractHook(inputs, unit);
                if (hookResult != null) {
                    ts = hookResult;
                }
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

    protected abstract void iterate(
            Iterable<HELD_ITEM> newItemsSource,
            Function<HELD_ITEM, HELD_ITEM> push
    );

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
            PredicateCollection<INNER_ITEM, ?> heldItemBooleanFunction
    );

    protected abstract boolean isWorkSpotReadyForItem(
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
        ArrayList<WorkPosition<POS>> shuffled = makeMutableShuffledCopy(extra, workSpots.alternates());
        if (workSpots.preferredValue() != null) {
            shuffled.removeIf(v -> v.equals(workSpots.preferredValue()));
            shuffled.add(0, workSpots.preferredValue());
        }
        for (WorkPosition<POS> workSpot : shuffled) {
            WorkOutput<TOWN, WorkPosition<POS>> v = tryWorking(extra, workSpot);
            if (v != null && (v.worked() || v.claimed())) {
                return getWithSurfaceInteractionPos(extra, v);
            }
        }
        return getWithSurfaceInteractionPos(
                extra, new WorkOutput<>(
                        false,
                        false,
                        null,
                        ImmutableList.copyOf(shuffled).get(0)
                )
        );
    }

    protected abstract WorkOutput<TOWN, WorkPosition<POS>> getWithSurfaceInteractionPos(
            EXTRA extra,
            WorkOutput<TOWN, WorkPosition<POS>> v
    );

    protected abstract ArrayList<WorkPosition<POS>> makeMutableShuffledCopy(
            EXTRA extra,
            Collection<WorkPosition<POS>> workSpots
    );

    public @Nullable WorkOutput<@Nullable TOWN, WorkPosition<POS>> tryWorking(
            EXTRA extra,
            WorkPosition<POS> workSpot
    ) {
        if (!isServerUpAndTownDataReadable(extra)) {
            return null;
        }
        boolean canClaim = getWorkStatuses(extra).canClaim(workSpot.jobBlock(), () -> this.claimSpots.apply(extra));

        ticksSinceLastAction++;
        if (ticksSinceLastAction < interval) {
            if (canClaim) {
                return new WorkOutput<>(false, true, null, workSpot);
            }
            return null;
        }
        ticksSinceLastAction = 0;

        if (!canClaim) {
            return null;
        }

        WorkOutput<TOWN, WorkPosition<POS>> vNull = new WorkOutput<>(false, true, null, workSpot);

        if (!isEntityClose(extra, workSpot.jobBlock())) {
            return vNull;
        }

        ImmutableWorkStateContainer<POS, TOWN> workStatuses = getWorkStatuses(extra);
        State jobBlockState = workStatuses.getJobBlockState(workSpot.jobBlock());

        int action = Util.withFallbackForNullInput(jobBlockState, State::processingState, 0);
        if (action >= maxState) {
            if (jobBlockState != null && jobBlockState.workLeft() == 0) {
                TOWN ex = tryExtractProduct(extra, workSpot.jobBlock());
                return new WorkOutput<>(true, true, ex, workSpot);
            }
        }

        PredicateCollection<INNER_ITEM, ?> tool = checks.getToolsForStep(action);
        if (tool != null && !tool.isEmpty()) {
            Collection<HELD_ITEM> items = getHeldItems(extra, villagerIndex);
            boolean foundTool = items.stream().anyMatch(i -> tool.test(i.get()));
            if (!foundTool) {
                return vNull;
            }
        }

        TOWN initTown = getTown(extra);
        PredicateCollection<HELD_ITEM, HELD_ITEM> ingredientsForStep = this.checks.getIngredientsForStep(action);
        if (ingredientsForStep != null && !ingredientsForStep.isEmpty()) {
            WorkedSpot<POS> wsBefore = getWorkedSpotWithUpToDateState(extra, initTown, workSpot.jobBlock());
            InsertResult<TOWN, HELD_ITEM> o = itemWI.tryInsertIngredients(
                    extra,
                    ingredientsForStep,
                    wsBefore
            );
            if (o == null) {
                @SuppressWarnings("DataFlowIssue") int quantityWanted = checks.getQuantityForStep(action, 0);
                if (jobBlockState != null && jobBlockState.ingredientCount() < quantityWanted) {
                    return new WorkOutput<>(false, true, initTown, workSpot);
                }
            } else {
                TOWN ctx = o.contextAfterInsert();
                HELD_ITEM item = o.itemBeforeInsert();
                @Nullable TOWN out = postInsertHook(
                        ctx,
                        extra,
                        getWorkedSpotWithUpToDateState(extra, ctx, workSpot.jobBlock()).withBefore(wsBefore.state()),
                        item,
                        maxState
                );
                if (out == null) {
                    out = ctx;
                }
                return new WorkOutput<>(true, true, out, workSpot);
            }
        }

        if (jobBlockState == null) {
            jobBlockState = State.fresh();
        }

        if (this.checks.isWorkRequiredAtStep(action)) {
            int work = this.checks.getWorkForStep(action, 0);
            if (work > 0) {
                if (action == 0) {
                    if (jobBlockState.workLeft() == 0) {
                        TOWN town = workStatuses.setJobBlockState(
                                workSpot.jobBlock(),
                                jobBlockState.setWorkLeft(work)
                        );
                        return new WorkOutput<>(false, true, town, workSpot);
                    }
                }
            }
        }

        // TODO: If workspot is waiting for time, return  null

        TOWN town = workWI.tryWork(
                extra, getWorkedSpotWithUpToDateState(extra, initTown, workSpot.jobBlock()), jobBlockState.workLeft() <= 1
        );
        return new WorkOutput<>(town != null, town != null, town, workSpot);
    }

    protected abstract WorkedSpot<POS> getWorkedSpotWithUpToDateState(
            EXTRA extra,
            TOWN stateSource,
            POS workSpot
    );

    protected abstract Collection<HELD_ITEM> getHeldItems(
            EXTRA extra,
            int villagerIndex
    );

    @Override
    public @Nullable Integer getIngredientQuantityRequiredAtState(
            int state,
            @Nullable Integer orDefault
    ) {
        return checks.getQuantityForStep(state, 0);
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
            } else {
                getResetFunc(inputs, position).apply(getTown(inputs));
            }
            if (town == null) {
                Collection<HELD_ITEM> items = getHeldItems(inputs, villagerIndex);
                ImmutableList<HELD_ITEM> generatedResult = getResults(inputs, items);

                town = tryGiveItems(inputs, generatedResult, position);

                if (town != null && ImmutableList.copyOf(generatedResult).stream().anyMatch(v -> !v.isEmpty())) {
                    extractionListeners.forEach(l -> l.accept(inputs, position));
                }
            }
            if (town != null) {
                triggerCompletionAdvancement(inputs, position);
                jobCompletedListeners.forEach(r -> r.accept(jobId));
            }
            return town;
            // TODO: If SpecialRules.NULLIFY_EXCESS_RESULTS does not apply, should we spawn items in town?
        }
        return null;
    }

    protected abstract void triggerCompletionAdvancement(EXTRA inputs,
                                                         POS position
    );

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
            HELD_ITEM item,
            int maxState
    ) {
        ProductionStatus o = ProductionStatus.fromJobBlockStatus(position.previousState(), maxState);
        Collection<String> rules = specialRules.get(o);
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
    private @Nullable TOWN postExtractHook(
            EXTRA inputs,
            HELD_ITEM item
    ) {
        Collection<String> rules = specialRules.get(ProductionStatus.EXTRACTING_PRODUCT);
        if (rules == null || rules.isEmpty()) {
            return null;
        }
        return postExtractHook(getTown(inputs), rules, inputs, getTownPos(inputs), item);
    }

    protected abstract POS getTownPos(EXTRA inputs);

    protected abstract @Nullable TOWN preExtractHook(
            TOWN town,
            Collection<String> rules,
            EXTRA inputs,
            POS position
    );

    protected abstract TOWN postExtractHook(
            TOWN town,
            Collection<String> rules,
            EXTRA inputs,
            POS position,
            HELD_ITEM extractedItem
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

    protected abstract boolean isStacked(INNER_ITEM innerItem);

    protected abstract TOWN getTown(EXTRA inputs);

    protected abstract ImmutableList<HELD_ITEM> getResults(
            EXTRA inputs,
            Collection<HELD_ITEM> items
    );

    protected abstract boolean isEntityClose(
            EXTRA extra,
            POS position
    );

    protected abstract boolean isServerUpAndTownDataReadable(EXTRA extra);

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

    public void addItemExtractionListener(BiConsumer<EXTRA, POS> listener) {
        this.extractionListeners.add(listener);
    }

    public void removeItemInsertionListener(TriConsumer<EXTRA, POS, HELD_ITEM> listener) {
        this.itemWI.removeItemInsertionListener(listener);
    }

    public void addJobCompletionListener(Consumer<JobID> listener) {
        this.jobCompletedListeners.add(listener);
    }

    public void removeJobCompletionListener(Consumer<JobID> listener) {
        this.jobCompletedListeners.remove(listener);
    }

    public abstract boolean tryGrabbingInsertedSupplies(
            EXTRA mcExtra
    );

    public abstract int timesInserted(EXTRA extra);

    public @Nullable WorkPosition<POS> getWorkSpot() {
        return workspot.value;
    }

    public void setWorkSpot(WithReason<@Nullable WorkPosition<POS>> o) {
        this.workspot = o;
    }

    @Override
    public @Nullable PredicateCollection<HELD_ITEM, ?> getIngredientsRequiredAtState(Integer state) {
        return checks.getIngredientsForStep(state);
    }

    public void registerUnmetNeeds(
            EXTRA extra,
            @Nullable POS workspot,
            int timesInserted
    ) {
        needsReg.addUnmet(extra, workspot, timesInserted);
    }

    protected abstract void registerUnmetNeed(
            EXTRA extra,
            NeedsRegistrations.Need need
    );

    public void registerUnmetRooms(
            EXTRA extra
    ) {
        needsReg.addUnmetRoom(extra);
    }

    protected abstract void registerUnmetRoom(
            EXTRA extra
    );
}
