package ca.bradj.questown.jobs;

import ca.bradj.questown.QT;
import ca.bradj.questown.commands.DebugLogArgument;
import ca.bradj.questown.core.Config;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class DeclarativeJobTicker<TOWN, POS, HELD_ITEM> {

    public interface Dependencies<TOWN, POS, HELD_ITEM> {
        WorkStatusHandle<POS, HELD_ITEM> getWorkStatusHandle(TOWN town);
    }

    public static void tick(
            BiConsumer<String, Object[]> logger
    ) {
        WorkStatusHandle<BlockPos, MCHeldItem> work = getWorkStatusHandle(town);
        AtomicReference<RoomsNeedingVillagerInput<MCRoom, ResourceLocation, BlockPos>> rniot = new AtomicReference<>(
                roomsNeedingIngredientsOrTools(
                        town,
                        work::getJobBlockState,
                        (BlockPos bp) -> work.canClaim(bp, this.claimSupplier)
                ));

        VisitorMobEntity vme = (VisitorMobEntity) entity;
        ImmutableList<MCHeldItem> heldItems = vme.getJobJournalSnapshot().items();
        Function<BlockPos, @NotNull State> bsFn = bp -> Util.applyOrDefault(
                bp,
                p -> town.getWorkStatusHandle(ownerUUID).getJobBlockState(p),
                State.fresh()
        );

        boolean firstTick = this.isFirstTick;
        if (this.isFirstTick) {
            this.isFirstTick = false;
        }

        Supplier<ImmutableList<BlockPos>> otherVillagerPositions = () -> town.getVillagerHandle().entities().stream()
                                                                             .filter(v -> !ownerUUID.equals(v.getUUID()))
                                                                             .map(
                                                                                     Entity::getOnPos)
                                                                             .collect(ImmutableList.toImmutableList());
        Supplier<BlockPos> randomWalkableTownPosition = () -> town.getRandomWanderTarget(entity.getOnPos());
        UnsafeVillagerData villagerData = town.getVillagerHandle().getUnprotectedDataHandle(vme.getVUID());

        PreTickHook.run(
                specialGlobalRules,
                town::getServerLevel,
                location,
                heldItems,
                fn -> rniot.set(fn.apply(rniot.get())),
                bsFn,
                firstTick,
                entity.blockPosition(),
                otherVillagerPositions,
                randomWalkableTownPosition,
                villagerData
        );
        specialRules.forEach((state, rules) -> PreTickHook.run(
                rules,
                town::getServerLevel,
                location,
                heldItems,
                fn -> rniot.set(fn.apply(rniot.get())),
                bsFn,
                firstTick,
                entity.blockPosition(),
                otherVillagerPositions,
                randomWalkableTownPosition,
                villagerData
        ));

        this.roomsNeedingIngredientsOrTools = new RoomsNeedingVillagerInput<>(rniot.get().get());

        MCExtra extra = new MCExtra(town, work, (VisitorMobEntity) entity);
        this.tick(extra, work, entity, facingPos, this.roomsNeedingIngredientsOrTools, statusFactory);
    }

    @Override
    protected void tick(
            MCExtra extra,
            WorkStatusHandle<BlockPos, MCHeldItem> work,
            LivingEntity entity,
            Direction facingPos,
            // Change this to a supplier whose value is cached for one tick
            RoomsNeedingVillagerInput<MCRoom, ResourceLocation, BlockPos> roomsNeedingIngredientsOrTools,
            IProductionStatusFactory<ProductionStatus> statusFactory
    ) {
        JobTownProvider<MCRoom> jtp = makeTownProviderForTick(extra, work, roomsNeedingIngredientsOrTools);

        EntityCurrentJobSite<MCRoom> entityCurrentJobSite = Jobs.getEntityCurrentJobSite(
                entity.blockPosition(),
                roomsNeedingIngredientsOrTools,
                jtp.roomsWithCompletedProduct()
        );

        EntityLocStateProvider<MCRoom> elp = new EntityLocStateProvider<>() {
            @Override
            public @Nullable MCRoom getEntityCurrentJobSite() {
                if (entityCurrentJobSite == null) {
                    return null;
                }
                return entityCurrentJobSite.room();
            }
        };

        Supplier<ProductionStatus> computeState = getStateComputer(extra.town(), statusFactory, jtp, elp);
        this.signal = Signals.fromDayTime(Util.getDayTime(extra.town().getServerLevel()));
        WorkPosition<BlockPos> workSpot = world.getWorkSpot();
        BlockPos bp = Util.orNull(workSpot, WorkPosition::jobBlock);
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
                this.asLogicWorld(
                        extra,
                        work,
                        (VisitorMobEntity) entity,
                        entityCurrentJobSite,
                        roomsNeedingIngredientsOrTools
                ),
                (tuwn, bpp) -> Util.withFallbackForNullInput(
                        getWorkStatusHandle(extra.town()).getJobBlockState(bpp),
                        State::processingState,
                        0
                ),
                Config.WORKED_RECENTLY_TICKS.get().intValue()
        );
    }
}
