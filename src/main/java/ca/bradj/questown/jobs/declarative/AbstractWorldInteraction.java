package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.jobs.*;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.logic.PredicateCollection;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.Util;
import ca.bradj.questown.mobs.helperchicken.ChickenArcLootGuarantee;
import ca.bradj.questown.town.AbstractWorkStatusStore;
import ca.bradj.questown.town.Claim;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import ca.bradj.questown.town.interfaces.ImmutableWorkStateContainer;
import ca.bradj.questown.town.workstatus.State;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
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
            protected boolean canInsertItem(
                    EXTRA extra,
                    HELD_ITEM item,
                    POS bp
            ) {
                return self.canInsertItem(extra, item, bp);
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

            @Override
            protected TOWN onWorkActionCompleted(
                    EXTRA extra,
                    TOWN town
            ) {
                countProficiencyBearingActionForTest(self.getProficiencyId());
                return self.onWorkActionCompleted(extra, town);
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
        QT.JOB_LOGGER.debug("[tryGiveItems] Initial stack size: {}, items: {}",
                stack.size(),
                stack.stream().map(h -> h.get().quantity()).toList());

        TOWN ts = getTown(inputs);
        if (stack.isEmpty()) {
            QT.JOB_LOGGER.debug("[tryGiveItems] No result items during extraction — firing hooks with null item");
            TOWN hooked = postExtractHook(inputs, ts, null);
            return reset.apply(hooked != null ? hooked : ts);
        }

        boolean gotAll = false;
        int i = -1;
        for (HELD_ITEM item : getHeldItems(inputs, villagerIndex)) {
            i++;
            if (!item.isEmpty()) {
                continue;
            }
            HELD_ITEM newItem = stack.pop();
            QT.JOB_LOGGER.debug("[tryGiveItems] Popped item qty={}, isMulti={}",
                    newItem.get().quantity(), isMulti(newItem.get()));
            if (isMulti(newItem.get())) {
                HELD_ITEM shrunk = newItem.shrink();
                QT.JOB_LOGGER.debug("[tryGiveItems] Pushing shrunk item qty={}", shrunk.get().quantity());
                stack.push(shrunk);
            }
            HELD_ITEM unit = newItem.unit();
            TOWN hooked = postExtractHook(inputs, ts, unit);
            ts = setHeldItem(inputs, hooked != null ? hooked : ts, villagerIndex, i, unit);
            QT.VILLAGER_LOGGER.debug("Villager took {}", unit.toShortString());

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

    /**
     * Pushes {@code newItemsSource}'s items onto {@code push}, bounded by
     * {@code Config.BASE_MAX_LOOP} via {@link Util#iterate}. The shared lane for both the
     * realtime and warp implementations (previously spelled out identically in each);
     * the JUnit double overrides with an unbounded {@code forEach}.
     */
    protected void iterate(
            Iterable<HELD_ITEM> newItemsSource,
            Function<HELD_ITEM, HELD_ITEM> push
    ) {
        Util.iterate(newItemsSource, push::apply);
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

    protected JobID getJobId() {
        return jobId;
    }

    /**
     * Completed work actions of proficiency-bearing jobs since the last reset, counted where the
     * realtime and warp paths both funnel through. The proficiency-parity autotest (ADR-0010) needs
     * a measure of "how much work happened" that is independent of the proficiency code under test:
     * produced items can't serve, since a townie's held stack and a chest stack don't count alike.
     * Jobs declaring no proficiency-id (the work seeker, every shipped job today) are excluded, or
     * their actions would inflate the divisor and understate the per-action gain.
     * <p>
     * Process-global and never reset outside tests: every town and every villager increments the
     * same counter, so only a single-worker scenario can read it as "the work under test".
     */
    private static long proficiencyBearingActionsForTest = 0;

    private static void countProficiencyBearingActionForTest(@Nullable String proficiencyId) {
        if (proficiencyId == null) {
            return;
        }
        proficiencyBearingActionsForTest++;
    }

    /**
     * The proficiency-id of this interaction's job, or null when it declares none. Resolved by the
     * two real paths through {@code ServerJobsRegistry}; the default keeps JUnit's world-interaction
     * doubles away from the server registry, which they never initialize. Real paths must
     * override — the null default means a new subclass is silently inert, with no failure.
     */
    protected @Nullable String getProficiencyId() {
        return null;
    }

    /**
     * Scale an of-10 work speed by a proficiency multiplier, keeping the result inside the 1–10
     * band {@link State#decrWork} accepts. Without the ceiling a
     * townie who is both well-fed (base 10) and better than break-even at their job (multiplier
     * above 1) computes a speed of 11+ and throws, taking down the whole work loop — the warp
     * loop's exception handler turns that into a silently lost visit. Shared by both lanes
     * (ADR-0010).
     */
    static int applyProficiencyToWorkSpeed(
            int baseOf10,
            float multiplier
    ) {
        return Math.max(1, Math.min(10, Math.round(baseOf10 * multiplier)));
    }

    /**
     * The work-speed multiplier for the current job's proficiency-id (1× if the job declares none).
     * Both paths fold this into their {@code of-10} speed at {@code getWorkSpeedOf10} so effective
     * production rate stays warp/realtime-symmetric (ADR-0010).
     */
    static float proficiencyMultiplier(
            @Nullable String proficiencyId,
            Function<String, Float> levelForId
    ) {
        if (proficiencyId == null) {
            return 1f;
        }
        return Proficiency.multiplierFor(
                levelForId.apply(proficiencyId),
                Config.PROFICIENCY_MIN_MULTIPLIER.get().floatValue(),
                Config.PROFICIENCY_MAX_MULTIPLIER.get().floatValue()
        );
    }

    /**
     * One completed work action on the townie's proficiency map, with the mod-config gain/decay
     * (the lane-facing entry point both {@code onWorkActionCompleted} lanes call — ADR-0010,
     * identical inputs). The write-back stays on the lane: realtime mutates the villager
     * handle, warp returns a new {@code MCTownState}.
     */
    protected Map<String, Float> levelProficiencies(
            Map<String, Float> current,
            @Nullable String proficiencyId
    ) {
        return levelProficiencies(
                current,
                proficiencyId,
                Config.PROFICIENCY_GAIN_PER_TICK.get().floatValue(),
                Config.PROFICIENCY_DECAY_PER_TICK.get().floatValue()
        );
    }

    /**
     * The pure seam of {@link #levelProficiencies(Map, String)}: same gain/decay/interval math
     * with the values supplied by the caller, so JUnit (where the mod config is never loaded)
     * can drive it directly.
     */
    protected Map<String, Float> levelProficiencies(
            Map<String, Float> current,
            @Nullable String proficiencyId,
            float gainPerTick,
            float decayPerTick
    ) {
        if (proficiencyId == null) {
            return current;
        }
        return Proficiency.levelUp(current, proficiencyId, gainPerTick, decayPerTick, interval);
    }

    public static long getProficiencyBearingActionsForTest() {
        return proficiencyBearingActionsForTest;
    }

    public static void resetProficiencyBearingActionsForTest() {
        proficiencyBearingActionsForTest = 0;
    }

    /**
     * Called once per completed work action (see {@link AbstractWorkWI#onWorkActionCompleted}).
     * The proficiency-leveling seam; default passes {@code town} through unchanged.
     */
    protected TOWN onWorkActionCompleted(
            EXTRA extra,
            TOWN town
    ) {
        return town;
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

    /**
     * The live server level backing this lane's {@code extra}, or null when the lane has none
     * (the JUnit double). The realtime lane reads it off the town; the warp lane carries it in
     * its {@code Inputs}.
     */
    protected @Nullable ServerLevel getServerLevel(EXTRA extra) {
        return null;
    }

    protected ArrayList<WorkPosition<POS>> shuffle(
            EXTRA extra,
            Collection<WorkPosition<POS>> workSpots
    ) {
        return new ArrayList<>(Compat.shuffle(ImmutableList.copyOf(workSpots), getServerLevel(extra)));
    }

    /**
     * BlockPos-specialized (vanilla {@link Util#workWithSurfaceInteractionPos}), so it stays a
     * lane seam rather than moving to the {@code POS}-generic base; both lanes pass their
     * {@link #getServerLevel} through unchanged.
     */
    protected abstract WorkOutput<TOWN, WorkPosition<POS>> getWithSurfaceInteractionPos(
            EXTRA extra,
            WorkOutput<TOWN, WorkPosition<POS>> v
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
            WorkedSpot<POS> wsBefore = getCurWorkedSpot(extra, initTown, workSpot.jobBlock());
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
                        getCurWorkedSpot(extra, ctx, workSpot.jobBlock()).withBefore(wsBefore.state()),
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
                extra, getCurWorkedSpot(extra, initTown, workSpot.jobBlock()), jobBlockState.workLeft() <= 1
        );
        return new WorkOutput<>(town != null, town != null, town, workSpot);
    }

    protected abstract WorkedSpot<POS> getCurWorkedSpot(
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
            if (town == null) {
                Collection<HELD_ITEM> items = getHeldItems(inputs, villagerIndex);
                Iterable<HELD_ITEM> generatedResult = getResults(inputs, items);

                town = tryGiveItems(inputs, generatedResult, position);

                if (town != null && ImmutableList.copyOf(generatedResult).stream().anyMatch(v -> !v.isEmpty())) {
                    extractionListeners.forEach(l -> l.accept(inputs, position));
                }
            }
            if (town != null) {
                town = getResetFunc(inputs, position).apply(town);
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
        return postInsertHook(ctx, rules, inputs, position, item);
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
    protected @Nullable TOWN postExtractHook(
            EXTRA inputs,
            TOWN currentState,
            @Nullable HELD_ITEM item
    ) {
        // Always notify a non-empty extraction on the unconditional extract path.
        // The live engine (RealtimeWorldInteraction) overrides this to close a
        // matching LOOT quest. Deliberately NOT gated by the
        // EXTRACTING_PRODUCT special-rule check below — the quest check is
        // independent of the job's post-extract rule (see LootExtractGuard).
        onLootExtracted(inputs, item);
        Collection<String> rules = specialRules.get(ProductionStatus.EXTRACTING_PRODUCT);
        if (rules == null || rules.isEmpty()) {
            return currentState;
        }
        return postExtractHook(currentState, rules, inputs, getTownPos(inputs), item);
    }

    /**
     * Fired on every extraction (including the null/empty placeholder). Forge-
     * free no-op by default; the live engine overrides it to close matching LOOT
     * quests. Kept separate from the rule-gated 5-arg hook so a job that has no
     * EXTRACTING_PRODUCT rule (e.g. the sword hunter) still closes its quest.
     */
    protected void onLootExtracted(EXTRA inputs, @Nullable HELD_ITEM item) {}

    /**
     * Whether this job registered any special rule under
     * {@link ProductionStatus#EXTRACTING_PRODUCT}. Passed to
     * {@link LootExtractGuard#questCheckFires} for its contract documentation;
     * the guard itself does not consult it.
     */
    protected boolean hasExtractingProductRule() {
        Collection<String> rules = specialRules.get(ProductionStatus.EXTRACTING_PRODUCT);
        return rules != null && !rules.isEmpty();
    }

    /**
     * Runs generated results through the once-per-town loot guarantees (the chicken-arc
     * worldly-seeds and the first-hunter raw-food). The lane-shared half of {@code getResults};
     * the lanes differ only in how they resolve the town flag that holds the guarantee bits
     * (ADR-0014).
     */
    protected Iterable<MCHeldItem> withLootGuarantees(
            @Nullable TownFlagBlockEntity flag,
            Iterable<MCHeldItem> results
    ) {
        results = ChickenArcLootGuarantee.maybePrepend(flag, results);
        results = FirstHunterLootGuarantee.maybePrepend(flag, getJobId(), results);
        return results;
    }

    /**
     * The town-flag block entity at {@code pos}, or null when the level or block entity is
     * missing. The lookup both lanes share when resolving the loot-guarantee flag.
     */
    protected static @Nullable TownFlagBlockEntity flagBlockEntityAt(
            @Nullable ServerLevel level,
            @Nullable BlockPos pos
    ) {
        if (level == null || pos == null) {
            return null;
        }
        return level.getBlockEntity(pos) instanceof TownFlagBlockEntity flag ? flag : null;
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
            @Nullable HELD_ITEM extractedItem
    );

    protected abstract TOWN setJobBlockState(
            @NotNull EXTRA inputs,
            TOWN ts,
            POS position,
            State fresh
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

    public void tryExtractWithNoItem(EXTRA inputs) {
        postExtractHook(inputs, getTown(inputs), null);
    }

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
 
 
