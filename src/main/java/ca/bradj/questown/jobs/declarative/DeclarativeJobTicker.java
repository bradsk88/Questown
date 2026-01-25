package ca.bradj.questown.jobs.declarative;

/*
 * ARCHITECTURAL NOTE: Declarative Job Consolidation
 * ==================================================
 *
 * This package is intentionally package-private to encourage consolidation of
 * declarative job logic. Historically, the codebase evolved with job logic spread
 * across multiple utility classes, static methods, and interfaces (e.g., JobsClean,
 * ContainersClean, various *WI classes) to enable isolated unit testing.
 *
 * While testability is valuable, this fragmentation led to:
 * - Multiple implementations of the same logic
 * - Unclear canonical behavior for declarative jobs
 * - Complex dependency graphs between utility classes
 *
 * The goal is for DeclarativeJob to work EXACTLY ONE WAY, with all logic living
 * in this package using CONCRETE implementations rather than:
 * - Accepting interfaces that could have multiple implementations
 * - Delegating to generic static utility methods
 * - Allowing external code (e.g. unit tests) to modify core job behavior
 *
 * When adding new CORE functionality:
 * - Prefer adding methods directly to classes in this package
 * - Avoid creating new utility classes outside this package
 * - Move existing scattered logic into this package over time
 * - Use concrete types from DeclarativeJobTickerDependencies, not abstractions
 *
 * Testing should be done via integration tests that exercise the full job flow,
 * in addition to unit tests of isolated utility methods.
 */

import ca.bradj.questown.core.UtilClean;
import ca.bradj.questown.integration.jobs.UnsafeVillagerData;
import ca.bradj.questown.jobs.*;
import ca.bradj.questown.jobs.declarative.nomc.WorkSeekerJob;
import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.jobs.production.RoomsNeedingVillagerInput;
import ca.bradj.questown.mc.Util;
import ca.bradj.questown.town.AbstractWorkStatusStore;
import ca.bradj.questown.town.interfaces.WorkStatusHandle;
import ca.bradj.questown.town.workstatus.State;
import ca.bradj.roomrecipes.adapter.IRoomRecipeMatch;
import ca.bradj.roomrecipes.core.Room;
import ca.bradj.roomrecipes.core.space.Position;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.*;

class DeclarativeJobTicker<POS, HELD_ITEM extends Item<HELD_ITEM>, ROOM extends Room, MATCH extends IRoomRecipeMatch<ROOM, ?, POS, ?>, EXTRA, TOWN, LOCATION> {


    private final int maxState;
    private final JobLogic<EXTRA, TOWN, POS> logic;

    public interface EntityHandle<POS, HELD_ITEM> {
        POS getBlockPosition();

        ImmutableList<HELD_ITEM> getHeldItems();
    }

    public interface Dependencies<POS, RECIPE, HELD_ITEM, TOWN_ITEM extends Item<TOWN_ITEM>, ROOM extends Room, MATCH extends IRoomRecipeMatch<ROOM, ?, POS, ?>, EXTRA, LOCATION> extends
            Dependencies2<ROOM, MATCH, POS, HELD_ITEM, TOWN_ITEM>, Dependencies3<RECIPE>, Dependencies4<HELD_ITEM> {
        AbstractWorkStatusStore<POS, HELD_ITEM, ROOM, ?> getWorkStatusHandle();

        <X> RoomsNeedingVillagerInput<ROOM, X, POS> computeRoomsNeedingInput(
                WorkStatusHandle<POS, HELD_ITEM> work
        );

        EntityHandle<POS, HELD_ITEM> getEntity();

        Supplier<ImmutableList<POS>> getOtherVillagerPositions();

        Supplier<POS> getRandomWanderTarget(POS avoiding);

        UnsafeVillagerData getVillagerData();

        <X> void runPreTickHook(
                Collection<String> rules,
                LOCATION location,
                ImmutableList<HELD_ITEM> heldItems,
                Consumer<Function<RoomsNeedingVillagerInput<ROOM, X, POS>, RoomsNeedingVillagerInput<ROOM, X, POS>>> roomsReplacer,
                Function<POS, State> blockStateFunction,
                boolean firstTick,
                POS entityPosition,
                Supplier<ImmutableList<POS>> otherVillagerPositions,
                Supplier<POS> randomWalkTarget,
                UnsafeVillagerData villagerData
        );

        void cacheRoomsNeedingInput(RoomsNeedingVillagerInput<ROOM, ?, POS> rniot2);

        Position toPosition(POS blockPosition);

        boolean isSimilarYCoord(
                POS blockPosition,
                ROOM room
        );

        // Job identity and configuration
        JobID getJobId();

        ExpirationRules getExpiration();

        int getWorkInterval();

        int getWorkedRecentlyTicks();

        // Entity and owner
        UUID getOwnerUUID();

        // State providers
        boolean isInventoryEmpty();

        @Nullable
        Integer getWorkForStep(int step);

        ImmutableList<String> getSpecialGlobalRules();

        boolean isLogicWrappingUp();

        // World interaction
        AbstractWorldInteraction<?, POS, ?, ?, ?> getWorldInteraction();

        @Nullable
        ContainerTarget<?, ?> getSuccessTarget();

        @Nullable
        EntityCurrentJobSite<ROOM> getEntityCurrentJobSite(
                RoomsNeedingVillagerInput<ROOM, ?, POS> rniot
        );

        <X> RoomsNeedingVillagerInput<ROOM, X, POS> getCachedRoomsNeedingInput();

        ImmutableList<? extends Predicate<?>> getRecipe(Integer state);

        boolean prioritizesExtraction();

        boolean hasInserted(int action);

        <RECIPE> DeclarativeLogicWorld.WorldDeps<POS, HELD_ITEM, ROOM> createWorldDependencies(
                RoomsNeedingVillagerInput<ROOM, RECIPE, POS> rniot,
                @Nullable EntityCurrentJobSite<ROOM> entityCurrentJobSite
        );

        /**
         * Returns the "extra" context object used by AbstractWorldInteraction.
         * For MC implementations, this is typically MCExtra.
         */
        EXTRA getExtra();
    }

    private final ImmutableList<String> specialGlobalRules;
    private final ImmutableMap<?, Collection<String>> specialRules;
    private final LOCATION location;
    private boolean isFirstTick = true;

    public DeclarativeJobTicker(
            ImmutableList<String> specialGlobalRules,
            ImmutableMap<?, Collection<String>> specialRules,
            LOCATION location,
            int maxState
    ) {
        this.specialGlobalRules = specialGlobalRules;
        this.specialRules = specialRules;
        this.location = location;
        this.maxState = maxState;
        this.logic = new JobLogic<>();
    }

    // Expose logic state for DeclarativeJob

    public boolean hasWorkedRecently() {
        return logic.hasWorkedRecently();
    }

    public @Nullable WorkPosition<POS> workSpot() {
        return logic.workSpot();
    }

    public boolean isWrappingUp() {
        return logic.isWrappingUp();
    }

    public <RECIPE> void tick(
            Dependencies<POS, RECIPE, HELD_ITEM, ?, ROOM, MATCH, EXTRA, LOCATION> dependencies,
            BiConsumer<String, Object[]> logger
    ) {
        WorkStatusHandle<POS, HELD_ITEM> work = dependencies.getWorkStatusHandle();
        AtomicReference<RoomsNeedingVillagerInput<ROOM, RECIPE, POS>> rniot = new AtomicReference<>(
                dependencies.computeRoomsNeedingInput(work)
        );

        EntityHandle<POS, HELD_ITEM> entity = dependencies.getEntity();
        ImmutableList<HELD_ITEM> heldItems = entity.getHeldItems();
        Function<POS, State> bsFn = bp -> UtilClean.applyOrDefault(
                bp,
                work::getJobBlockState,
                State.fresh()
        );

        boolean firstTick = this.isFirstTick;
        if (this.isFirstTick) {
            this.isFirstTick = false;
        }
        Supplier<ImmutableList<POS>> otherVillagerPositions = dependencies.getOtherVillagerPositions();
        Supplier<POS> randomWalkableTownPosition = dependencies.getRandomWanderTarget(entity.getBlockPosition());
        UnsafeVillagerData villagerData = dependencies.getVillagerData();

        dependencies.runPreTickHook(
                specialGlobalRules,
                location,
                heldItems,
                fn -> rniot.set(unsafe(fn.apply(unsafe(rniot.get())))),
                bsFn,
                firstTick,
                entity.getBlockPosition(),
                otherVillagerPositions,
                randomWalkableTownPosition,
                villagerData
        );
        specialRules.forEach((state, rules) -> dependencies.runPreTickHook(
                rules,
                location,
                heldItems,
                fn -> rniot.set(unsafe(fn.apply(unsafe(rniot.get())))),
                bsFn,
                firstTick,
                entity.getBlockPosition(),
                otherVillagerPositions,
                randomWalkableTownPosition,
                villagerData
        ));

        // TODO: Remaining lines need more abstraction
        RoomsNeedingVillagerInput<ROOM, RECIPE, POS> rniot2 = new RoomsNeedingVillagerInput<>(rniot.get().get());
        dependencies.cacheRoomsNeedingInput(rniot2);
//
//        MCExtra extra = new MCExtra(town, work, (VisitorMobEntity) entity);
        this.tick(dependencies, work, rniot2);
    }

    @SuppressWarnings("unchecked")
    private <X, Y> RoomsNeedingVillagerInput<ROOM, Y, POS> unsafe(
            RoomsNeedingVillagerInput<ROOM, X, POS> rn
    ) {
        return (RoomsNeedingVillagerInput<ROOM, Y, POS>) rn;
    }

    private <TOWN_ITEM extends Item<TOWN_ITEM>, RECIPE> void tick(
            Dependencies<POS, RECIPE, HELD_ITEM, TOWN_ITEM, ROOM, MATCH, EXTRA, ?> deps,
            WorkStatusHandle<POS, HELD_ITEM> work,
            RoomsNeedingVillagerInput<ROOM, RECIPE, POS> rniot2
    ) {
        JobTownProvider<ROOM> jtp = new TickTownProvider
                <ROOM, POS, MATCH, HELD_ITEM, TOWN_ITEM, ContainerTarget<?, TOWN_ITEM>>
                (
                        deps::getRoomsWithCompletedProduct,
                        deps::getJobSites,
                        deps::getRoomsForSupplyCheck,
                        deps.isJobSitePredicate(),
                        deps::toBlock,
                        work::getJobBlockState,
                        work::getTimeToNextState,
                        rniot2,
                        deps::isJobBlock,
                        deps::canClaim,
                        deps::item,
                        deps::tools,
                        deps::stringify,
                        maxState,
                        deps::convert,
                        deps::hasSpace,
                        deps::getDayTime
                );

        // Compute entityCurrentJobSite for uses that need a snapshot (e.g., logic.tick parameters)
        EntityCurrentJobSite<ROOM> entityCurrentJobSite = getEntityCurrentJobSite(deps, rniot2);

        // Make EntityLocStateProvider compute the job site lazily on each call.
        // This ensures roomsWithCompletedProduct is fresh when status is computed.
        EntityLocStateProvider<ROOM> elp = new EntityLocStateProvider<>() {
            @Override
            public @Nullable ROOM getEntityCurrentJobSite() {
                EntityCurrentJobSite<ROOM> current = DeclarativeJobTicker.this.getEntityCurrentJobSite(deps, rniot2);
                if (current == null) {
                    return null;
                }
                return current.room();
            }
        };

        Supplier<ProductionStatus> computeState = getStateComputer(deps, jtp, elp);
        WorkPosition<POS> workSpot = deps.getWorkSpot();
        POS bp = UtilClean.orNull(workSpot, WorkPosition::jobBlock);
        int action = bp == null ? 0 : Util.withFallbackForNullInput(
                work.getJobBlockState(bp),
                State::processingState,
                0
        );
        JobID jobId = deps.getJobId();
        EXTRA extra = deps.getExtra();
        logic.tick(
                extra,
                computeState,
                jobId,
                entityCurrentJobSite != null,
                WorkSeekerJob.isSeekingWork(jobId),
                workSpot != null && deps.hasInserted(action),
                !deps.isInventoryEmpty(),
                deps.getExpiration(),
                new JobLogic.JobDetails(maxState, deps.getWorkForStep(0), deps.getWorkInterval()),
                (JobLogic.JLWorld<EXTRA, TOWN, POS>) (Object) new DeclarativeLogicWorld<POS, HELD_ITEM, ROOM>(deps.createWorldDependencies(rniot2, entityCurrentJobSite)),
                (unused, bpp) -> Util.withFallbackForNullInput(
                        work.getJobBlockState((POS) bpp),
                        State::processingState,
                        0
                ),
                deps.getWorkedRecentlyTicks()
        );
    }


    @SuppressWarnings({"unchecked", "rawtypes"})
    protected <RECIPE, TOWN_ITEM extends Item<TOWN_ITEM>> @NotNull Supplier<ProductionStatus> getStateComputer(
            Dependencies<POS, RECIPE, HELD_ITEM, TOWN_ITEM, ROOM, MATCH, EXTRA, ?> deps,
            JobTownProvider<ROOM> jtp,
            EntityLocStateProvider<ROOM> elp
    ) {
        return () -> {
            ProductionStatus s = deps.getComputeStatusOverrideForSpecialJobs();
            if (s != null) {
                return s;
            }
            ProductionJournal journal = deps.getJournal();
            journal.tryUpdateStatus(jtp, elp, defaultEntityInvProvider(deps), DeclarativeJobs.STATUS_FACTORY, deps.prioritizesExtraction());
            return journal.getStatus();
        };
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    protected <RECIPE> EntityInvStateProvider<Integer> defaultEntityInvProvider(
            Dependencies<POS, RECIPE, HELD_ITEM, ?, ROOM, MATCH, EXTRA, ?> deps
    ) {
        return new EntityInvStateProvider<>() {
            @Override
            public boolean inventoryFull() {
                return deps.getJournal().isInventoryFull();
            }

            @Override
            public boolean hasNonSupplyItems() {
                RoomsNeedingVillagerInput<ROOM, RECIPE, POS> roomsNeedingIngredientsOrTools = deps.getCachedRoomsNeedingInput();
                Set<Integer> statesToFeed = roomsNeedingIngredientsOrTools.getNonEmptyStates();
                ImmutableList<Predicate<?>> allFillableRecipes = ImmutableList.copyOf(
                        statesToFeed.stream()
                                    .flatMap(v -> deps.getRecipe(v).stream())
                                    .toList()
                );
                return JobsClean.hasNonSupplyItems((ItemsHolder) deps.getJournal(), (ImmutableList) allFillableRecipes);
            }

            @Override
            public Map<Integer, SupplyItemStatus> getSupplyItemStatus() {
                return DeclarativeJobTicker.getSupplyItemStatuses(deps);
            }
        };
    }


    static <I extends Item<I>> ImmutableMap<Integer, SupplyItemStatus> getSupplyItemStatuses(
            Dependencies4<I> deps
    ) {
        SupplyChecks<I> checks = deps.asChecks();
        return getSupplyItemStatuses(
                deps.getJournalItemsSupplier(),
                checks.getIngredientsForStep(),
                checks::isIngredientRequiredAtStep,
                checks.getToolsForStep(),
                checks::isToolRequiredAtStep,
                checks.getWorkRequiredAtStep(),
                deps.getMaxState()
        );
    }

    /**
     * @deprecated Use version that takes Checks
     */
    @Deprecated(forRemoval = true)
    @NotNull
    static <I extends Item<I>> ImmutableMap<Integer, SupplyItemStatus> getSupplyItemStatuses(
            Supplier<? extends Collection<I>> journal,
            Map<Integer, ? extends Predicate<I>> ingredientsRequiredAtStates,
            Function<Integer, Boolean> anyIngredientsRequiredAtStates,
            Map<Integer, ? extends Predicate<I>> toolsRequiredAtStates,
            Function<Integer, Boolean> anyToolsRequiredAtStates,
            Map<Integer, Integer> workRequiredAtStates,
            int maxState
    ) {
        HashMap<Integer, SupplyItemStatus> b = new HashMap<>();
        BiConsumer<Integer, Predicate<I>> fn = (state, ingr) -> {
            if (ingr == null) {
                if (!b.containsKey(state)) {
                    b.put(state, SupplyItemStatus.NOT_REQUIRED);
                }
                return;
            }

            // The check passes if the worker has ALL the ingredients needed for the state
            boolean hasItem = journal.get().stream().anyMatch(ingr);
            boolean neededOrUnknown = b.getOrDefault(state, SupplyItemStatus.NEEDS_ITEM) == SupplyItemStatus.NEEDS_ITEM;
            if (neededOrUnknown) {
                b.put(state, hasItem ? SupplyItemStatus.HAS_ITEM : SupplyItemStatus.NEEDS_ITEM);
            }
        };
        ingredientsRequiredAtStates.forEach(fn);
        toolsRequiredAtStates.forEach(fn);
        for (Map.Entry<Integer, Integer> work : workRequiredAtStates.entrySet()) {
            if (!anyIngredientsRequiredAtStates.apply(work.getKey()) && !anyToolsRequiredAtStates.apply(work.getKey())) {
                b.put(work.getKey(), SupplyItemStatus.NOT_REQUIRED);
            }
        }
        for (int i = 0; i < maxState; i++) {
            fn.accept(i, null);
        }
        return ImmutableMap.copyOf(b);
    }

    private <RECIPE> EntityCurrentJobSite<ROOM> getEntityCurrentJobSite(
            Dependencies<POS, RECIPE, ?, ?, ROOM, ?, EXTRA, ?> deps,
            RoomsNeedingVillagerInput<ROOM, RECIPE, POS> roomsNeedingVillagerInput
    ) {
        return JobsClean.getEntityCurrentJobSite(
                deps.toPosition(deps.getEntity().getBlockPosition()),
                roomsNeedingVillagerInput,
                deps.getRoomsWithCompletedProduct().stream().map(IRoomRecipeMatch::getRoom).toList(),
                room -> deps.isSimilarYCoord(deps.getEntity().getBlockPosition(), room),
                deps::isFarm
        );
    }
}
