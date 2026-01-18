package ca.bradj.questown.jobs;

import ca.bradj.questown.QT;
import ca.bradj.questown.commands.DebugLogArgument;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.core.UtilClean;
import ca.bradj.questown.integration.jobs.UnsafeVillagerData;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.jobs.declarative.MCExtra;
import ca.bradj.questown.jobs.declarative.PreTickHook;
import ca.bradj.questown.jobs.declarative.nomc.WorkSeekerJob;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.jobs.production.RoomsNeedingVillagerInput;
import ca.bradj.questown.mc.Util;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.interfaces.WorkStatusHandle;
import ca.bradj.questown.town.workstatus.State;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.Collection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class DeclarativeJobTicker<POS, HELD_ITEM, ROOM> {

    public interface EntityHandle<POS, HELD_ITEM> {
        POS getBlockPosition();
        ImmutableList<HELD_ITEM> getHeldItems();
    }

    public interface Dependencies<POS, HELD_ITEM, ROOM> {
        WorkStatusHandle<POS, HELD_ITEM> getWorkStatusHandle();

        RoomsNeedingVillagerInput<ROOM, ?, POS> computeRoomsNeedingInput(
                WorkStatusHandle<POS, HELD_ITEM> work
        );

        EntityHandle<POS, HELD_ITEM> getEntity();

        Supplier<ImmutableList<POS>> getOtherVillagerPositions();

        Supplier<POS> getRandomWanderTarget(POS avoiding);

        UnsafeVillagerData getVillagerData();

        void runPreTickHook(
                Collection<String> rules,
                WorkLocation location,
                ImmutableList<HELD_ITEM> heldItems,
                Consumer<Function<RoomsNeedingVillagerInput<ROOM, ?, POS>, RoomsNeedingVillagerInput<ROOM, ?, POS>>> roomsReplacer,
                Function<POS, State> blockStateFunction,
                boolean firstTick,
                POS entityPosition,
                Supplier<ImmutableList<POS>> otherVillagerPositions,
                Supplier<POS> randomWalkTarget,
                UnsafeVillagerData villagerData
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
            Dependencies<POS, HELD_ITEM, ROOM> dependencies,
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
//        this.roomsNeedingIngredientsOrTools = new RoomsNeedingVillagerInput<>(rniot.get().get());
//
//        MCExtra extra = new MCExtra(town, work, (VisitorMobEntity) entity);
//        this.tick(extra, work, entity, facingPos, this.roomsNeedingIngredientsOrTools, statusFactory);
    }

    // TODO: Abstract parameters to remove MC dependencies
//    @Override
//    private void tick(
//            MCExtra extra,
//            WorkStatusHandle<BlockPos, MCHeldItem> work,
//            LivingEntity entity,
//            Direction facingPos,
//            // Change this to a supplier whose value is cached for one tick
//            RoomsNeedingVillagerInput<MCRoom, ResourceLocation, BlockPos> roomsNeedingIngredientsOrTools,
//            IProductionStatusFactory<ProductionStatus> statusFactory
//    ) {
//        JobTownProvider<MCRoom> jtp = makeTownProviderForTick(extra, work, roomsNeedingIngredientsOrTools);
//
//        EntityCurrentJobSite<MCRoom> entityCurrentJobSite = Jobs.getEntityCurrentJobSite(
//                entity.blockPosition(),
//                roomsNeedingIngredientsOrTools,
//                jtp.roomsWithCompletedProduct()
//        );
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
//    }
}
