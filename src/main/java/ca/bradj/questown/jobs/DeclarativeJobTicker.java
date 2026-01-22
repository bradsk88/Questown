package ca.bradj.questown.jobs;

import ca.bradj.questown.core.UtilClean;
import ca.bradj.questown.integration.jobs.UnsafeVillagerData;
import ca.bradj.questown.jobs.declarative.ProductionJournal;
import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.jobs.production.ProductionJob;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.jobs.production.RoomsNeedingVillagerInput;
import ca.bradj.questown.mc.Util;
import ca.bradj.questown.town.interfaces.WorkStatusHandle;
import ca.bradj.questown.town.workstatus.State;
import ca.bradj.roomrecipes.adapter.IRoomRecipeMatch;
import ca.bradj.roomrecipes.core.Room;
import ca.bradj.roomrecipes.core.space.Position;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.*;

public class DeclarativeJobTicker<POS, HELD_ITEM, ROOM extends Room, MATCH extends IRoomRecipeMatch<ROOM, ?, POS, ?>, EXTRA, TOWN> {


    private final int maxState;
    private final JobLogic<EXTRA, TOWN, POS> logic;

    public interface EntityHandle<POS, HELD_ITEM> {
        POS getBlockPosition();

        ImmutableList<HELD_ITEM> getHeldItems();
    }

    public interface Dependencies<POS, RECIPE, HELD_ITEM, TOWN_ITEM extends Item<TOWN_ITEM>, ROOM extends Room, MATCH extends IRoomRecipeMatch<ROOM, ?, POS, ?>> extends
            Dependencies2<ROOM, MATCH, POS, HELD_ITEM, TOWN_ITEM>, Dependencies3<RECIPE> {
        WorkStatusHandle<POS, HELD_ITEM> getWorkStatusHandle();

        <X> RoomsNeedingVillagerInput<ROOM, X, POS> computeRoomsNeedingInput(
                WorkStatusHandle<POS, HELD_ITEM> work
        );

        EntityHandle<POS, HELD_ITEM> getEntity();

        Supplier<ImmutableList<POS>> getOtherVillagerPositions();

        Supplier<POS> getRandomWanderTarget(POS avoiding);

        UnsafeVillagerData getVillagerData();

        <X> void runPreTickHook(
                Collection<String> rules,
                WorkLocation location,
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
    }

    private final ImmutableList<String> specialGlobalRules;
    private final ImmutableMap<?, Collection<String>> specialRules;
    private final WorkLocation location;
    private boolean isFirstTick = true;

    public DeclarativeJobTicker(
            ImmutableList<String> specialGlobalRules,
            ImmutableMap<?, Collection<String>> specialRules,
            WorkLocation location,
            int maxState
    ) {
        this.specialGlobalRules = specialGlobalRules;
        this.specialRules = specialRules;
        this.location = location;
        this.maxState = maxState;
        this.logic = new JobLogic<>();
    }

    public <RECIPE> void tick(
            Dependencies<POS, RECIPE, HELD_ITEM, ?, ROOM, MATCH> dependencies,
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

    // TODO: Abstract parameters to remove MC dependencies
//    @Override
    private <TOWN_ITEM extends Item<TOWN_ITEM>, RECIPE> void tick(
            Dependencies<POS, RECIPE, HELD_ITEM, TOWN_ITEM, ROOM, MATCH> deps,
            EXTRA extra,
            WorkStatusHandle<POS, HELD_ITEM> work,
//            LivingEntity entity,
//            Direction facingPos,
//            // Change this to a supplier whose value is cached for one tick
//            RoomsNeedingVillagerInput<MCRoom, ResourceLocation, BlockPos> roomsNeedingIngredientsOrTools,
//            IProductionStatusFactory<ProductionStatus> statusFactory
            RoomsNeedingVillagerInput<ROOM, RECIPE, POS> rniot2
    ) {
        JobTownProvider<ROOM> jtp = new TickTownProvider
                <ROOM, POS, MATCH, HELD_ITEM, TOWN_ITEM, ContainerTarget<?, TOWN_ITEM>>
                (
                        deps::getRoomsWithCompletedProduct,
                        deps::getJobSites,
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
                        deps::hasSpace
                );
//
        EntityCurrentJobSite<ROOM> entityCurrentJobSite = getEntityCurrentJobSite(deps, rniot2);

        EntityLocStateProvider<ROOM> elp = new EntityLocStateProvider<>() {
            @Override
            public @Nullable ROOM getEntityCurrentJobSite() {
                if (entityCurrentJobSite == null) {
                    return null;
                }
                return entityCurrentJobSite.room();
            }
        };

        Supplier<ProductionStatus> computeState = getStateComputer(deps, jtp, elp);
        Signals signal = Signals.fromDayTime(deps.getDayTime());
        WorkPosition<POS> workSpot = deps.getWorkSpot();
        POS bp = UtilClean.orNull(workSpot, WorkPosition::jobBlock);
        int action = bp == null ? 0 : Util.withFallbackForNullInput(
                work.getJobBlockState(bp),
                State::processingState,
                0
        );
        logic.tick(
                extra,
                computeState,
                jobId,
                entityCurrentJobSite != null,
                WorkSeekerJob.isSeekingWork(jobId),
                workSpot != null && hasInserted(action),
                !inventory.isEmpty(),
                expiration,
                new JobLogic.JobDetails(maxState, checks.getWorkForStep(0), workInterval),
                new DeclarativeLogicWorld<>(town),
//                this.asLogicWorld(
//                        extra,
//                        work,
//                        (VisitorMobEntity) entity,
//                        entityCurrentJobSite,
//                        roomsNeedingIngredientsOrTools
//                ),
                (tuwn, bpp) -> Util.withFallbackForNullInput(
                        getWorkStatusHandle(extra.town()).getJobBlockState(bpp),
                        State::processingState,
                        0
                ),
                Config.WORKED_RECENTLY_TICKS.get().intValue()
        );
    }


    protected @NotNull Supplier<ProductionStatus> getStateComputer(
            Dependencies3<?> dep,
            JobTownProvider<ROOM> jtp,
            EntityLocStateProvider<ROOM> elp
    ) {
        return () -> {

//            if (FetcherHack.isFetcher(jobId)) {
//                ProductionStatus s = FetcherHack.computeStatus(town, journal.getItems());
//                if (s != null) {
//                    journal.changeStatus(s);
//                    return s;
//                }
//            }

            ProductionStatus s = dep.getComputeStatusOverrideForSpecialJobs();
            if (s != null) {
                return s;
            }
            ProductionJournal<?, ?> journal = dep.getJournal();
            journal.tryUpdateStatus(jtp, elp, defaultEntityInvProvider(), ProductionStatus.FACTORY, prioritizesExtraction());
            return journal.getStatus();
        };
    }

    protected EntityInvStateProvider<Integer> defaultEntityInvProvider() {
        return new EntityInvStateProvider<>() {
            @Override
            public boolean inventoryFull() {
                return journal.isInventoryFull();
            }

            @Override
            public boolean hasNonSupplyItems() {

                Set<Integer> statesToFeed = roomsNeedingIngredientsOrTools.getNonEmptyStates();
                ImmutableList<Predicate<?>> allFillableRecipes = ImmutableList.copyOf(
                        statesToFeed.stream()
                                    .flatMap(v -> getRecipe(v)
                                            .stream())
                                    .toList()
                );
                return Jobs.hasNonSupplyItems(journal, allFillableRecipes);
            }

            @Override
            public Map<Integer, SupplyItemStatus> getSupplyItemStatus() {
                return ProductionJob.this.getSupplyItemStatus();
            }
        };
    }

    private <RECIPE> EntityCurrentJobSite<ROOM> getEntityCurrentJobSite(
            Dependencies<POS, RECIPE, ?, ?, ROOM, ?> deps,
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
