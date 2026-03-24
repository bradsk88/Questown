package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.QT;
import ca.bradj.questown.commands.DebugLogArgument;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.core.UtilClean;
import ca.bradj.questown.core.VillagerUUID;
import ca.bradj.questown.integration.jobs.UnsafeVillagerData;
import ca.bradj.questown.integration.minecraft.MCContainer;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.*;
import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.jobs.production.RoomsNeedingVillagerInput;
import ca.bradj.questown.logic.PredicateCollection;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.Util;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.AbstractWorkStatusStore;
import ca.bradj.questown.town.TownContainers;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import ca.bradj.questown.town.special.SpecialQuests;
import ca.bradj.questown.town.workstatus.State;
import ca.bradj.questown.world.MinecraftWorldAccess;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatches;
import ca.bradj.roomrecipes.core.space.Position;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class DeclarativeJobTickerDependencies implements
        DeclarativeJobTicker.Dependencies<BlockPos, ResourceLocation, MCHeldItem, MCTownItem, MCRoom, RoomRecipeMatch<MCRoom>, MCExtra, WorkLocation> {
    private final TownFlagBlockEntity town;
    private final DeclarativeJob job;
    private final VisitorMobEntity entity;

    public DeclarativeJobTickerDependencies(
            DeclarativeJob declarativeJob,
            TownFlagBlockEntity town,
            VisitorMobEntity vme
    ) {
        this.town = town;
        this.job = declarativeJob;
        this.entity = vme;
    }

    @Override
    public boolean isJobBlock(BlockPos blockPos) {
        return job.isJobBlock(blockPos);
    }

    @Override
    public ImmutableList<RoomRecipeMatch<MCRoom>> getJobSites() {
        // Use getRoomsMatching() instead of getMatches() because it has
        // special handling for farms (stored in activeFarms, not activeRecipes)
        return town.getRoomHandle()
                   .getRoomsMatching(job.location().baseRoom())
                   .stream()
                   .map(v -> new RoomRecipeMatches<>(v.room, v.getRecipeIDs(), v.containedBlocks.entrySet()))
                   .collect(ImmutableList.toImmutableList());
    }

    @Override
    public ImmutableList<RoomRecipeMatch<MCRoom>> getRoomsForSupplyCheck() {
        // Return ALL rooms - same as old behavior with predicate (r) -> true
        return town.getRoomHandle()
                   .getMatches(m -> true)
                   .stream()
                   .map(v -> new RoomRecipeMatches<>(v.room, v.getRecipeIDs(), v.containedBlocks.entrySet()))
                   .collect(ImmutableList.toImmutableList());
    }

    @Override
    public Predicate<RoomRecipeMatch<MCRoom>> isJobSitePredicate() {
        return m -> m.getRecipeIDs().contains(job.location().baseRoom());
    }

    @Override
    public ContainersClean.Block<ContainerTarget<?, MCTownItem>> toBlock(
            MCRoom room,
            BlockPos blockPos
    ) {
        return new ContainersClean.Block<ContainerTarget<?, MCTownItem>>() {
            @Override
            public boolean isAir() {
                return town.getServerLevel().isEmptyBlock(blockPos);
            }

            @Override
            public boolean isJobBlock() {
                return job.isJobBlock(blockPos);
            }

            @Override
            public @Nullable ContainerTarget<MCContainer, MCTownItem> asContainer() {
                return TownContainers.fromEntity(town.getServerLevel(), blockPos);
            }

            @SuppressWarnings("DataFlowIssue")
            @Override
            public @Nullable ContainerTarget<MCContainer, MCTownItem> asChest() {
                Block b = town.getServerLevel().getBlockState(blockPos).getBlock();
                if (!(b instanceof ChestBlock cb)) {
                    return null;
                }
                return TownContainers.fromChestBlock(
                        room,
                        blockPos,
                        cb,
                        town.getServerLevel()
                );
            }
        };
    }

    @Override
    public boolean canClaim(BlockPos blockPos) {
        return getWorkStatusHandle(town).canClaim(blockPos, job.getClaimSupplier());
    }

    @Override
    public PredicateCollection<MCHeldItem, MCHeldItem> item(Integer integer) {
        return job.getChecks().getIngredientsForStep(integer);
    }

    @Override
    public Integer getQuantityForStep(int state) {
        return job.getChecks().getQuantityForStep(state, null);
    }

    @Override
    public PredicateCollection<MCTownItem, MCTownItem> tools(Integer integer) {
        return job.getChecks().getToolsForStep(integer);
    }

    @Override
    public String stringify(BlockPos blockPos) {
        return blockPos.toShortString();
    }

    @Override
    public MCHeldItem convert(MCTownItem mcTownItem) {
        return MCHeldItem.fromTown(mcTownItem);
    }

    /**
     * Gets the appropriate work status handle for this job.
     *
     * Jobs with SHARED_WORK_STATUS use the global (null owner) store where any villager
     * can continue work started by another.
     *
     * Jobs WITHOUT SHARED_WORK_STATUS (which includes jobs with CLAIM_SPOT) use per-owner
     * stores where each villager has isolated work states.
     *
     * NOTE: Jobs with CLAIM_SPOT implicitly use per-owner stores because claiming a
     * workspot only makes sense if the work state is isolated to that villager.
     */
    private AbstractWorkStatusStore<BlockPos, MCHeldItem, MCRoom, ServerLevel> getWorkStatusHandle(TownFlagBlockEntity town) {
        AbstractWorkStatusStore<BlockPos, MCHeldItem, MCRoom, ServerLevel> work;
        if (job.specialGlobalRules.contains(SpecialRules.SHARED_WORK_STATUS)) {
            work = town.getRealWorkStatusHandle(null);
        } else {
            work = town.getRealWorkStatusHandle(job.getOwnerUUID());
        }
        return work;
    }

    @Override
    public AbstractWorkStatusStore<BlockPos, MCHeldItem, MCRoom, ServerLevel> getWorkStatusHandle() {
            return getWorkStatusHandle(town);
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
                         .filter(v -> !UtilClean.sameUUID(job.getOwnerUUID(), v.getUUID()))
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
                () -> new MinecraftWorldAccess(town.getServerLevel()),
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

    @Override
    public boolean isFarm(ResourceLocation x) {
        return SpecialQuests.FARM.equals(x);
    }

    @Override
    public boolean hasSpace() {
        return Jobs.townHasSpace(town);
    }

    @Override
    public WorkPosition<BlockPos> getWorkSpot() {
        return job.getWorld().getWorkSpot();
    }

    @Override
    public Signals.DayTime getDayTime() {
        return new Signals.DayTime(town.getServerLevel().getDayTime());
    }

    @Override
    public ProductionStatus getComputeStatusOverrideForSpecialJobs() {
        // Delegate to the job so subclasses like WorkSeekerJob can override
        return job.getComputeStatusOverrideForSpecialJobs(town, DeclarativeJobs.STATUS_FACTORY);
    }

    @Override
    public ProductionJournal<?, ?> getJournal() {
        return job.getJournal();
    }

    // New methods for DeclarativeJobTicker refactoring

    @Override
    public JobID getJobId() {
        return job.getId();
    }

    @Override
    public ExpirationRules getExpiration() {
        return job.getExpiration();
    }

    @Override
    public int getWorkInterval() {
        return job.getWorkInterval();
    }

    @Override
    public int getWorkedRecentlyTicks() {
        return Config.WORKED_RECENTLY_TICKS.get().intValue();
    }

    @Override
    public UUID getOwnerUUID() {
        return job.getOwnerUUID();
    }

    @Override
    public boolean isInventoryEmpty() {
        return job.getJournal().getItems().isEmpty();
    }

    @Override
    public @Nullable Integer getWorkForStep(int step) {
        return job.getChecks().getWorkForStep(step);
    }

    @Override
    public ImmutableList<String> getSpecialGlobalRules() {
        return job.specialGlobalRules;
    }

    @Override
    public boolean isLogicWrappingUp() {
        // TODO: This needs to be tracked properly via the ticker's logic
        return false;
    }

    @Override
    public AbstractWorldInteraction<?, BlockPos, ?, ?, ?> getWorldInteraction() {
        return job.getWorld();
    }

    @Override
    public @Nullable ContainerTarget<?, ?> getSuccessTarget() {
        return job.getSuccessTargetForDeps();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <X> RoomsNeedingVillagerInput<MCRoom, X, BlockPos> getCachedRoomsNeedingInput() {
        return (RoomsNeedingVillagerInput<MCRoom, X, BlockPos>) job.getRoomsNeedingInputForDeps();
    }

    @Override
    public ImmutableList<? extends Predicate<?>> getRecipe(Integer state) {
        return job.getRecipe(state);
    }

    @Override
    public boolean prioritizesExtraction() {
        return job.prioritizesExtraction();
    }

    @Override
    public boolean hasInserted(int action) {
        return job.hasInsertedForDeps(action);
    }

    @Override
    public MCExtra getExtra() {
        return new MCExtra(town, getWorkStatusHandle(), entity);
    }

    @Override
    public Collection<BlockPos> getContainedBlocks(RoomRecipeMatch<MCRoom> match) {
        return match.getContainedBlocks().keySet();
    }

    @Override
    public RoomsNeedingVillagerInput.NVIRoom<MCRoom, ResourceLocation, BlockPos> makeNVIRoom(
            RoomRecipeMatch<MCRoom> match,
            boolean dueToWorkOnly
    ) {
        return new RoomsNeedingVillagerInput.NVIRoom<>(match, dueToWorkOnly);
    }

    @Override
    public Supplier<ImmutableList<MCHeldItem>> getJournalItemsSupplier() {
        return () -> job.getJournal().getItems();
    }

    @Override
    public SupplyChecks<MCHeldItem> asChecks() {
        final DeclarativeJobChecks<MCExtra, MCHeldItem, MCTownItem, RoomRecipeMatch<MCRoom>, BlockPos> self = this.job.getChecks();
        return new SupplyChecks<>() {
            @Override
            public Map<Integer, ? extends Predicate<MCHeldItem>> getIngredientsForStep() {
                return self.getAllRequiredIngredients();
            }

            @Override
            public Boolean isIngredientRequiredAtStep(Integer integer) {
                return self.isIngredientRequiredAtStep(integer);
            }

            @Override
            public Map<Integer, ? extends Predicate<MCHeldItem>> getToolsForStep() {
                return Jobs.unTown(self.getAllRequiredTools());
            }

            @Override
            public Boolean isToolRequiredAtStep(Integer integer) {
                return self.isToolRequiredAtStep(integer);
            }

            @Override
            public Map<Integer, Integer> getWorkRequiredAtStep() {
                return self.getAllRequiredWork();
            }
        };
    }

    @Override
    public int getMaxState() {
        return job.getMaxState();
    }

    @Override
    public <RECIPE> DeclarativeLogicWorld.WorldDeps<BlockPos, MCHeldItem, MCRoom> createWorldDependencies(
            RoomsNeedingVillagerInput<MCRoom, RECIPE, BlockPos> rniot,
            @Nullable EntityCurrentJobSite<MCRoom> entityCurrentJobSite
    ) {
        MCExtra extra = new MCExtra(town, getWorkStatusHandle(), entity);
        DeclarativeJobTickerDependencies self = this;
        return new DeclarativeLogicWorld.WorldDeps<>() {
            @Override
            public BlockPos getEntityBlockPosition() {
                return entity.blockPosition();
            }

            @Override
            public Object getEntityVUID() {
                return entity.getVUID();
            }

            @Override
            public UUID getOwnerUUID() {
                return job.getOwnerUUID();
            }

            @Override
            public JobID getJobId() {
                return job.getId();
            }

            @Override
            public ImmutableList<String> getSpecialGlobalRules() {
                return job.specialGlobalRules;
            }

            @Override
            public void changeJobForVillager(UUID ownerUUID, JobID id, boolean flag) {
                town.getVillagerHandle().changeJobForVillager(ownerUUID, id, flag);
            }

            @Override
            public void changeJobForVisitorFromBoard(UUID ownerUUID, JobID currentJobId) {
                town.changeJobForVisitorFromBoard(ownerUUID, currentJobId);
            }

            @Override
            public Object getVillagerData(Object vuid) {
                return town.getVillagerHandle().getUnprotectedDataHandle((VillagerUUID) vuid);
            }

            @Override
            public void runPreMaxTicksJobChangeHook(ImmutableList<String> rules, Object villagerData) {
                PreMaxTicksJobChangeHook.run(rules, (UnsafeVillagerData) villagerData);
            }

            @Override
            public Collection<MCRoom> getRoomsMatchingClinic() {
                return town.getRoomHandle().getRoomsMatching(SpecialQuests.CLINIC)
                           .stream().map(v -> v.room).toList();
            }

            @Override
            public void registerFoundLoots(ImmutableList<MCHeldItem> items) {
                town.getKnowledgeHandle().registerFoundLoots(items);
            }

            @Override
            public boolean hasServerLevel() {
                return town.getServerLevel() != null;
            }

            @Override
            public BlockPos getRandomHorizontalFrom(BlockPos bp) {
                ServerLevel sl = town.getServerLevel();
                return bp.relative(Compat.getRandomHorizontal(sl));
            }

            @Override
            public void logDebug(String message) {
                town.getDebugLogger(QT.JOB_LOGGER, DebugLogArgument.VILLAGER_NAVIGATION).log(message);
            }

            @Override
            public State getJobBlockState(BlockPos pos) {
                return getWorkStatusHandle().getJobBlockState(pos);
            }

            @Override
            public void setJobBlockState(BlockPos pos, State state) {
                getWorkStatusHandle().setJobBlockState(pos, state);
            }

            @Override
            public void clearWorkState(BlockPos pos) {
                getWorkStatusHandle().clearState(pos);
            }

            @Override
            public WorkPosition<BlockPos> getWorldWorkSpot() {
                return job.getWorld().getWorkSpot();
            }

            @Override
            public boolean tryGrabbingInsertedSupplies() {
                return job.getWorld().tryGrabbingInsertedSupplies(extra);
            }

            @Override
            public void clearInsertedSupplies() {
                job.getWorld().clearInsertedSupplies(extra);
            }

            @Override
            public void registerUnmetNeeds(BlockPos workspot, int timesInserted) {
                job.getWorld().registerUnmetNeeds(extra, workspot, timesInserted);
            }

            @Override
            public void registerUnmetRooms() {
                job.getWorld().registerUnmetRooms(extra);
            }

            @Override
            public int timesInserted() {
                return job.getWorld().timesInserted(extra);
            }

            @Override
            public AbstractWorldInteraction<?, BlockPos, ?, ?, ?> getWorldHandle() {
                return job.getWorld();
            }

            @Override
            public Map<Integer, Collection<WorkPosition<BlockPos>>> listAllWorkSpots(
                    Function<BlockPos, State> getBlockState,
                    @Nullable EntityCurrentJobSite<MCRoom> jobSite,
                    Predicate<BlockPos> isValidWalkTarget,
                    Predicate<BlockPos> isJobBlock,
                    Function<BlockPos, BlockPos> getRandomAdjacent
            ) {
                return job.listAllWorkSpots(getBlockState, jobSite, isValidWalkTarget, isJobBlock, getRandomAdjacent);
            }

            @Override
            public boolean tryDropLoot(long tick, BlockPos entityBlockPos) {
                return job.tryDropLootForDeps(tick, entityBlockPos);
            }

            @SuppressWarnings({"unchecked", "rawtypes"})
            @Override
            public void tryGetSupplies(
                    RoomsNeedingVillagerInput<MCRoom, ?, BlockPos> roomsNeedingInput,
                    BlockPos entityBlockPos,
                    long currentTick
            ) {
                job.tryGetSuppliesForDeps(town, (RoomsNeedingVillagerInput) roomsNeedingInput, entityBlockPos, currentTick);
            }

            @Override
            public void setLookTarget(BlockPos position) {
                job.setLookTargetForDeps(position);
            }

            @Override
            public boolean shouldInitializeWorkState(BlockPos bp) {
                ServerLevel sl = town.getServerLevel();
                return job.location().shouldInitializeWorkState().test(Util.info(sl), bp);
            }

            @Override
            public boolean isValidWalkTarget(BlockPos bp) {
                return job.isValidWalkTargetForDeps(town, bp);
            }

            @Override
            public boolean isJobBlock(BlockPos bp) {
                return job.isJobBlock(bp);
            }

            @Override
            public boolean isLogicWrappingUp() {
                // TODO: Track this properly via the ticker's logic
                return false;
            }

            @Override
            public ImmutableList<MCHeldItem> getJournalItems() {
                return job.getJournal().getItems();
            }

            @Override
            public @Nullable EntityCurrentJobSite<MCRoom> getEntityCurrentJobSite() {
                return entityCurrentJobSite;
            }

            @SuppressWarnings("unchecked")
            @Override
            public RoomsNeedingVillagerInput<MCRoom, ?, BlockPos> getRoomsNeedingInput() {
                return rniot;
            }

            @Override
            public @Nullable BlockPos getSuccessTargetPOS() {
                ContainerTarget<?, ?> target = job.getSuccessTargetForDeps();
                return target != null ? target.getBlockPos() : null;
            }

            @Override
            public void runPostDropHook(
                    BlockPos successTargetPos,
                    ImmutableList<MCHeldItem> itemsBeforeDrop,
                    ImmutableList<MCHeldItem> itemsAfterDrop,
                    Consumer<BlockPos> clearState
            ) {
                PostDropHook.run(
                        town,
                        job.specialGlobalRules,
                        new MinecraftWorldAccess(town.getServerLevel()),
                        successTargetPos,
                        itemsBeforeDrop,
                        itemsAfterDrop,
                        clearState
                );
            }

            @Override
            public long getCurrentTick() {
                return Util.getTick(town.getServerLevel());
            }
        };
    }
}
