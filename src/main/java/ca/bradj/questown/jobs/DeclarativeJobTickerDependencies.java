package ca.bradj.questown.jobs;

import ca.bradj.questown.core.UtilClean;
import ca.bradj.questown.integration.jobs.UnsafeVillagerData;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.jobs.declarative.PreTickHook;
import ca.bradj.questown.jobs.production.RoomsNeedingVillagerInput;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.interfaces.WorkStatusHandle;
import ca.bradj.questown.town.workstatus.State;
import ca.bradj.roomrecipes.adapter.IRoomRecipeMatch;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatches;
import ca.bradj.roomrecipes.core.space.Position;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;

import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class DeclarativeJobTickerDependencies implements
        DeclarativeJobTicker.Dependencies<BlockPos, MCHeldItem, MCRoom, RoomRecipeMatches<MCRoom>> {
    private final TownInterface town;
    private final DeclarativeJob job;
    private final VisitorMobEntity entity;

    public DeclarativeJobTickerDependencies(DeclarativeJob declarativeJob,
                                            TownInterface town,
                                            VisitorMobEntity vme
    ) {
        this.town = town;
        this.job = declarativeJob;
        this.entity = vme;
    }

    @Override
    public ImmutableList<RoomRecipeMatches<MCRoom>> getRoomsWithCompletedProduct() {
        return ImmutableList.copyOf(job.roomsWithState.get(
                job.roomsMatching(town),
                DeclarativeJob.isCorrectBlock(town),
                getWorkStatusHandle(town)::getJobBlockState
        ).stream().map(this::pluralize).toList());
    }

    private RoomRecipeMatches<MCRoom> pluralize(RoomRecipeMatch<MCRoom> v) {
        return new RoomRecipeMatches<>(
                v.room,
                v.getRecipeIDs(),
                v.containedBlocks.entrySet()
        );
    }

    private WorkStatusHandle<BlockPos, MCHeldItem> getWorkStatusHandle(TownInterface town) {
        WorkStatusHandle<BlockPos, MCHeldItem> work;
        if (job.specialGlobalRules.contains(SpecialRules.SHARED_WORK_STATUS)) {
            work = town.getWorkStatusHandle(null);
        } else {
            work = town.getWorkStatusHandle(job.getOwnerUUID());
        }
        return work;
    }

    @Override
    public WorkStatusHandle<BlockPos, MCHeldItem> getWorkStatusHandle() {
        return getWorkStatusHandle(town);
    }

    @Override
    // TODO: Move logic into DeclarativeJobTicker
    public RoomsNeedingVillagerInput<MCRoom, ?, BlockPos> computeRoomsNeedingInput(
            WorkStatusHandle<BlockPos, MCHeldItem> work
    ) {
        return job.roomsNeedingIngredientsOrTools(
                town,
                work::getJobBlockState,
                (BlockPos bp) -> work.canClaim(bp, job.getClaimSupplier())
        );
    }

    @Override
    public DeclarativeJobTicker.EntityHandle<BlockPos, MCHeldItem> getEntity() {
        return new DeclarativeJobTicker.EntityHandle<>() {
            @Override
            public BlockPos getBlockPosition() {
                return entity.blockPosition();
            }

            @Override
            public ImmutableList<MCHeldItem> getHeldItems() {
                return entity.getJobJournalSnapshot().items();
            }
        };
    }

    @Override
    public Supplier<ImmutableList<BlockPos>> getOtherVillagerPositions() {
        return () -> town.getVillagerHandle().entities().stream()
                         .filter(v -> !UtilClean.sameUUID(job.getOwnerUUID(),v.getUUID()))
                         .map(Entity::getOnPos)
                         .collect(ImmutableList.toImmutableList());
    }

    @Override
    public Supplier<BlockPos> getRandomWanderTarget(BlockPos avoiding) {
        return () -> town.getRandomWanderTarget(avoiding);
    }

    @Override
    public UnsafeVillagerData getVillagerData() {
        return town.getVillagerHandle().getUnprotectedDataHandle(entity.getVUID());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public <X> void runPreTickHook(
            Collection<String> rules,
            WorkLocation location,
            ImmutableList<MCHeldItem> heldItems,
            Consumer<Function<RoomsNeedingVillagerInput<MCRoom, X, BlockPos>, RoomsNeedingVillagerInput<MCRoom, X, BlockPos>>> roomsReplacer,
            Function<BlockPos, State> blockStateFunction,
            boolean firstTick,
            BlockPos entityPosition,
            Supplier<ImmutableList<BlockPos>> otherVillagerPositions,
            Supplier<BlockPos> randomWalkTarget,
            UnsafeVillagerData villagerData
    ) {
        PreTickHook.run(
                rules,
                town::getServerLevel,
                location,
                heldItems,
                f -> roomsReplacer.accept((Function) f),
                blockStateFunction,
                firstTick,
                entityPosition,
                otherVillagerPositions,
                randomWalkTarget,
                villagerData
        );
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public void cacheRoomsNeedingInput(RoomsNeedingVillagerInput<MCRoom, ?, BlockPos> rniot2) {
        job.cacheRoomsNeedingIngredientsOrTools((RoomsNeedingVillagerInput) rniot2);
    }

    @Override
    public Position toPosition(BlockPos blockPosition) {
        return new Position(blockPosition.getX(), blockPosition.getZ());
    }

    @Override
    public boolean isSimilarYCoord(
            BlockPos entityBlockPos,
            MCRoom room
    ) {
        return (room.yCoord > entityBlockPos.getY() - 5) && (room.yCoord < entityBlockPos.getY() + 5);
    }
}
