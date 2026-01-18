package ca.bradj.questown.jobs;

import ca.bradj.questown.core.UtilClean;
import ca.bradj.questown.integration.jobs.UnsafeVillagerData;
import ca.bradj.questown.jobs.production.RoomsNeedingVillagerInput;
import ca.bradj.questown.town.interfaces.WorkStatusHandle;
import ca.bradj.questown.town.special.SpecialQuests;
import ca.bradj.questown.town.workstatus.State;
import ca.bradj.roomrecipes.adapter.IRoomRecipeMatch;
import ca.bradj.roomrecipes.adapter.Positions;
import ca.bradj.roomrecipes.core.Room;
import ca.bradj.roomrecipes.core.space.Position;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.Collection;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class DeclarativeJobTicker<POS, HELD_ITEM, ROOM extends Room, MATCH extends IRoomRecipeMatch<ROOM, ?, POS, ?>> {


    public interface EntityHandle<POS, HELD_ITEM> {
        POS getBlockPosition();
        ImmutableList<HELD_ITEM> getHeldItems();
    }

    public interface Dependencies<POS, HELD_ITEM, ROOM extends Room, MATCH extends IRoomRecipeMatch<ROOM, ?, ?, ?>> extends Dependencies2<ROOM, MATCH> {
        WorkStatusHandle<POS, HELD_ITEM> getWorkStatusHandle();

        RoomsNeedingVillagerInput<ROOM, ?, POS> computeRoomsNeedingInput(
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
            WorkLocation location
    ) {
        this.specialGlobalRules = specialGlobalRules;
        this.specialRules = specialRules;
        this.location = location;
    }

    public void tick(
            Dependencies<POS, HELD_ITEM, ROOM, MATCH> dependencies,
            BiConsumer<String, Object[]> logger
    ) {
        WorkStatusHandle<POS, HELD_ITEM> work = dependencies.getWorkStatusHandle();
        AtomicReference<RoomsNeedingVillagerInput<ROOM, ?, POS>> rniot = new AtomicReference<>(
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
                fn -> rniot.set(fn.apply(rniot.get())),
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
                fn -> rniot.set(fn.apply(rniot.get())),
                bsFn,
                firstTick,
                entity.getBlockPosition(),
                otherVillagerPositions,
                randomWalkableTownPosition,
                villagerData
        ));

        // TODO: Remaining lines need more abstraction
        RoomsNeedingVillagerInput<ROOM, ?, POS> rniot2 = new RoomsNeedingVillagerInput<>(rniot.get().get());
        dependencies.cacheRoomsNeedingInput(rniot2);
//
//        MCExtra extra = new MCExtra(town, work, (VisitorMobEntity) entity);
        this.tick(dependencies, rniot2);
    }

    // TODO: Abstract parameters to remove MC dependencies
//    @Override
    private void tick(
            Dependencies<POS, ?, ROOM, MATCH> deps,
//            MCExtra extra,
//            WorkStatusHandle<BlockPos, MCHeldItem> work,
//            LivingEntity entity,
//            Direction facingPos,
//            // Change this to a supplier whose value is cached for one tick
//            RoomsNeedingVillagerInput<MCRoom, ResourceLocation, BlockPos> roomsNeedingIngredientsOrTools,
//            IProductionStatusFactory<ProductionStatus> statusFactory
            RoomsNeedingVillagerInput<ROOM, ?, POS> rniot2
    ) {
        JobTownProvider<ROOM> jtp = new TickTownProvider<>(deps::getRoomsWithCompletedProduct);
//
        EntityCurrentJobSite<ROOM> entityCurrentJobSite = getEntityCurrrentJobSite(deps, rniot2);
//
//        EntityLocStateProvider<MCRoom> elp = new EntityLocStateProvider<>() {
//            @Override
//            public @Nullable MCRoom getEntityCurrentJobSite() {
//                if (entityCurrentJobSite == null) {
//                    return null;
//                }
//                return entityCurrentJobSite.room();
//            }
//        };
//
//        Supplier<ProductionStatus> computeState = getStateComputer(extra.town(), statusFactory, jtp, elp);
//        this.signal = Signals.fromDayTime(Util.getDayTime(extra.town().getServerLevel()));
//        WorkPosition<BlockPos> workSpot = world.getWorkSpot();
//        BlockPos bp = Util.orNull(workSpot, WorkPosition::jobBlock);
//        int action = bp == null ? 0 : Util.withFallbackForNullInput(
//                work.getJobBlockState(bp),
//                State::processingState,
//                0
//        );
//        logic.tick(
//                extra,
//                computeState,
//                jobId,
//                entityCurrentJobSite != null,
//                WorkSeekerJob.isSeekingWork(jobId),
//                workSpot != null && hasInserted(action),
//                !inventory.isEmpty(),
//                expiration,
//                new JobLogic.JobDetails(maxState, checks.getWorkForStep(0), workInterval),
//                this.asLogicWorld(
//                        extra,
//                        work,
//                        (VisitorMobEntity) entity,
//                        entityCurrentJobSite,
//                        roomsNeedingIngredientsOrTools
//                ),
//                (tuwn, bpp) -> Util.withFallbackForNullInput(
//                        getWorkStatusHandle(extra.town()).getJobBlockState(bpp),
//                        State::processingState,
//                        0
//                ),
//                Config.WORKED_RECENTLY_TICKS.get().intValue()
//        );
    }

    private EntityCurrentJobSite<ROOM> getEntityCurrrentJobSite(
            Dependencies<POS, ?, ROOM> deps,
            RoomsNeedingVillagerInput<ROOM, ?, POS> roomsNeedingVillagerInput
    ) {
        return JobsClean.getEntityCurrentJobSite(
                deps.toPosition(deps.getEntity().getBlockPosition()),
                roomsNeedingVillagerInput,
                deps.getRoomsWithCompletedProduct(),
                room -> deps.isSimilarYCoord(deps.getEntity().getBlockPosition(), room),
                deps::isFarm
        );
    }
}
