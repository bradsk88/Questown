package ca.bradj.questown.jobs.production;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.advancements.RoomTrigger;
import ca.bradj.questown.core.init.AdvancementsInit;
import ca.bradj.questown.integration.minecraft.MCContainer;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.*;
import ca.bradj.questown.jobs.declarative.MCExtra;
import ca.bradj.questown.jobs.declarative.WithReason;
import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.logic.PredicateCollection;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.Util;
import ca.bradj.questown.town.Claim;
import ca.bradj.questown.town.TownContainers;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.interfaces.WorkStatusHandle;
import ca.bradj.questown.town.workstatus.State;
import ca.bradj.roomrecipes.adapter.Positions;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerListener;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.Marker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static ca.bradj.questown.jobs.Jobs.isCloseTo;

/**
 * @param <STATUS>
 * @param <SNAPSHOT>
 * @param <JOURNAL>
 * @deprecated Use DeclarativeJob
 */
public abstract class ProductionJob<
        STATUS extends IProductionStatus<STATUS>,
        SNAPSHOT extends Snapshot<MCHeldItem>,
        JOURNAL extends Journal<STATUS, MCHeldItem, SNAPSHOT>
        > implements Job<MCHeldItem, SNAPSHOT, STATUS>, LockSlotHaver, ContainerListener,
        JournalItemsListener<MCHeldItem>, Jobs.LootDropper<MCHeldItem>, SignalSource {

    private @Nullable Long lastDropTick = null;
    private @Nullable Long secondLastDropTick = null;

    private final Marker marker;

    private final ArrayList<DataSlot> locks = new ArrayList<>();
    protected final Container inventory;
    protected final JOURNAL journal;
    protected final IProductionStatusFactory<STATUS> statusFactory;
    protected final Supplier<Claim> claimSupplier;
    private final WorkLocation location;
    protected ContainerTarget<MCContainer, MCTownItem> successTarget;
    protected ContainerTarget<MCContainer, MCTownItem> suppliesTarget;
    private boolean dropping;

    protected final UUID ownerUUID;

    // TODO: Stop using this - use a cached supplier instead
    protected RoomsNeedingVillagerInput<MCRoom, ResourceLocation, BlockPos> roomsNeedingIngredientsOrTools;

    public final ImmutableMap<STATUS, Collection<String>> specialRules;
    public final ImmutableList<String> specialGlobalRules;
    protected @Nullable BlockPos lookTarget;

    public @Nullable BlockPos getJobSite(
            TownInterface town
    ) {
        // Don't recompute jobsite if we already have a target.
        // But DO retry every once in a while to account for "stuck villager" bugs
        if (this.jobSite == null || Compat.nextRandomInt(town.getServerLevel(), 200) == 0) {
            ServerLevel sl = town.getServerLevel();
            if (sl == null) {
                return null;
            }
            WithReason<@Nullable BlockPos> js = findJobSite(
                    town,
                    roomsNeedingIngredientsOrTools,
                    getWorkStatusHandle(town)::getJobBlockState,
                    bp -> this.isValidWalkTarget(town, bp),
                    this::isJobBlock,
                    bp -> bp.relative(Compat.getRandomHorizontal(sl))
            );
            this.jobSite = js.value();
            if (this.jobSite != null) {
                AdvancementsInit.ROOM_TRIGGER.triggerForNearestPlayer(
                        town.getServerLevel(),
                        RoomTrigger.Triggers.FirstJobBlock,
                        this.jobSite
                );
            }
        }
        return jobSite;
    }

    protected abstract boolean isJobBlock(
            BlockPos bp
    );

    protected boolean isValidWalkTarget(
            TownInterface town,
            BlockPos bp
    ) {
        @Nullable ServerLevel sl = town.getServerLevel();
        if (sl == null) {
            return false;
        }
        Material footMaterial = sl.getBlockState(bp).getMaterial();
        boolean footSpotBlocked = footMaterial.isSolid();
        BlockState torsoMaterial = sl.getBlockState(bp.above());
        boolean torsoSpotBlocked = torsoMaterial.getMaterial().isSolid();
        BlockState groundMaterial = sl.getBlockState(bp.below());
        boolean groundSpotSolid = groundMaterial.getMaterial().isSolid();
        return groundSpotSolid && !(footSpotBlocked || torsoSpotBlocked);
    }

    private BlockPos jobSite;

    @Override
    public abstract Signals getSignal();

    protected void clearJobSite() {
        this.jobSite = null;
    }

    public boolean isDropping() {
        return dropping;
    }

    public interface RecipeProvider {
        ImmutableList<PredicateCollection<MCTownItem, ?>> getRecipe(int workState);
    }

    public ProductionJob(
            UUID ownerUUID,
            int inventoryCapacity,
            Marker logMarker,
            BiFunction<Integer, SignalSource, JOURNAL> journalInit,
            IProductionStatusFactory<STATUS> sFac,
            ImmutableMap<STATUS, Collection<String>> specialRules,
            ImmutableList<String> specialGlobalRules,
            Supplier<Claim> claimSupplier,
            WorkLocation location
    ) {
        // TODO: This is copy pasted. Reduce duplication.
        SimpleContainer sc = new SimpleContainer(inventoryCapacity) {
            @Override
            public int getMaxStackSize() {
                return 1;
            }
        };
        this.ownerUUID = ownerUUID;
        this.specialGlobalRules = specialGlobalRules;
        this.marker = logMarker;
        this.inventory = sc;
        sc.addListener(this);

        for (int i = 0; i < inventoryCapacity; i++) {
            this.locks.add(new LockSlot(i, this));
        }

        this.journal = journalInit.apply(inventoryCapacity, this);
        this.journal.addItemListener(this);

        this.statusFactory = sFac;

        this.specialRules = specialRules;
        this.claimSupplier = claimSupplier;
        this.location = location;
    }

    @Override
    public Function<Void, Void> addStatusListener(StatusListener o) {
        return this.journal.addStatusListener(o);
    }

    @Override
    public void removeStatusListener(StatusListener o) {
        this.journal.removeStatusListener(o);
    }

    @Override
    public Collection<? extends Runnable> notifyListenersOfNewJob(Function<StatusListener, Runnable> listenToNewJob) {
        return this.journal.notifyListenersOfNewJob(listenToNewJob);
    }

    @Override
    public STATUS getStatus() {
        return journal.getStatus();
    }

    @Override
    public boolean isWorking() {
        return isInitialized() && getStatus().isWorkingOnProduction();
    }

    @Override
    public String getStatusToSyncToClient() {
        return this.journal.getStatus()
                           .name();
    }

    @Override
    public void itemsChanged(ImmutableList<MCHeldItem> items) {
        Jobs.handleItemChanges(inventory, items);
    }

    @Override
    public UUID UUID() {
        return ownerUUID;
    }

    @Override
    public boolean isJumpingAllowed(BlockState onBlock) {
        return true;
    }

    @Override
    public boolean shouldHoldAllItems() {
        return !journal.hasAnyLootToDrop();
    }

    @Override
    public boolean hasAnyLootToDrop() {
        return journal.hasAnyLootToDrop();
    }

    @Override
    public Iterable<MCHeldItem> getItemsForDrop() {
        return journal.getItems();
    }

    @Override
    public boolean removeItem(MCHeldItem mct) {
        return journal.removeItem(mct);
    }

    protected abstract Map<Integer, SupplyItemStatus> getSupplyItemStatus();

    protected boolean tryDropLoot(
            Long currentTick,
            BlockPos entityPos
    ) {
        if (successTarget == null) {
            return false;
        }
        if (!isCloseTo(entityPos, successTarget.getBlockPos())) {
            return false;
        }
        if (!journal.getStatus()
                    .isDroppingLoot()) {
            return false;
        }
        if (this.dropping) {
            QT.JOB_LOGGER.debug(marker, "Trying to drop too quickly");
        }
        this.dropping = Jobs.tryDropLoot(this, entityPos, successTarget);
        if (this.dropping) {
            this.secondLastDropTick = this.lastDropTick;
            this.lastDropTick = currentTick;
        }
        return this.dropping;
    }

    @NotNull
    protected ImmutableList<PredicateCollection<MCTownItem, ?>> convertToCleanFns(
            Map<Integer, ? extends Collection<?>> statusMap
    ) {
        // TODO: Be smarter? We're just finding the first room that needs stuff.
        Optional<Integer> first = statusMap.entrySet()
                                           .stream()
                                           .filter(v -> !v.getValue()
                                                          .isEmpty())
                                           .map(Map.Entry::getKey)
                                           .findFirst();

        if (first.isEmpty()) {
            return ImmutableList.of();
        }

        return getRecipe(first.get());
    }

    protected abstract ImmutableList<PredicateCollection<MCTownItem, ?>> getRecipe(Integer integer);

    @Override
    public BlockPos getLook() {
        return lookTarget;
    }

    @Override
    public @Nullable BlockPos getTarget(
            BlockPos entityBlockPos,
            Vec3 entityPos,
            TownInterface town
    ) {
        @Nullable ServerLevel sl = town.getServerLevel();
        if (sl == null) {
            return null;
        }

        STATUS status = journal.getStatus();
        return doGetTarget(entityBlockPos, entityPos, town, status, sl);
    }

    private @Nullable BlockPos doGetTarget(
            BlockPos entityBlockPos,
            Vec3 entityPos,
            TownInterface town,
            STATUS status,
            @NotNull ServerLevel sl
    ) {
        if (status.isGoingToJobsite() || specialGlobalRules.contains(SpecialRules.ALWAYS_POPULATE_JOBSITE)) {
            BlockPos jobSite1 = getJobSite(town);
            this.setLookTarget(jobSite1); // TODO[ASAP]: Use special rule to determine non-job look target
            return jobSite1;
        }

        if (status.isWorkingOnProduction() || status.isWaitingForTimers()) {
            WorkPosition<BlockPos> productionSpot = findProductionSpot(sl);
            if (productionSpot != null) {
                this.setLookTarget(productionSpot.jobBlock());
                return productionSpot.entityFeetPos();
            }
            QT.JOB_LOGGER.debug("Production spot was null somehow");
            return null;
        }

        if (status.isDroppingLoot()) {
            successTarget = getDropTargetForLoot(entityBlockPos, town);
            if (successTarget != null) {
                this.setLookTarget(successTarget.getBlockPos());
                return Positions.ToBlock(successTarget.getInteractPosition(), successTarget.getYPosition());
            }
        }

        setupForGetSupplies(town, entityBlockPos, Util.getTick(sl));
        if (suppliesTarget != null) {
            this.setLookTarget(suppliesTarget.getBlockPos());
            return Positions.ToBlock(suppliesTarget.getInteractPosition(), suppliesTarget.getYPosition());
        }

        if (shouldDisappear(town, entityPos)) {
            return entityBlockPos;
        }

        return null;
    }

    protected @Nullable ContainerTarget<MCContainer, MCTownItem> getDropTargetForLoot(
            BlockPos entityBlockPos,
            TownInterface town
    ) {
        ImmutableList.Builder<MCTownItem> b = ImmutableList.builder();
        for (MCHeldItem item : journal.getItems()) {
            if (item.isEmpty()) {
                continue;
            }
            b.add(item.toItem());
        }
        return Jobs.setupForDropLoot(town, this.successTarget, entityBlockPos, b.build());
    }

    protected void setLookTarget(BlockPos jobSite1) {
        this.lookTarget = jobSite1;
    }

    protected abstract @Nullable WorkPosition<BlockPos> findProductionSpot(ServerLevel level);

    protected abstract WithReason<@Nullable BlockPos> findJobSite(
            TownInterface town,
            RoomsNeedingVillagerInput<MCRoom, ResourceLocation, BlockPos> blocks,
            Function<BlockPos, State> work,
            Predicate<BlockPos> isEmpty,
            Predicate<BlockPos> isJobBlock,
            Function<BlockPos, BlockPos> getRandomAdjacent
    );

    public abstract RoomsNeedingVillagerInput<MCRoom, ResourceLocation, BlockPos> roomsNeedingIngredientsOrTools(
            TownInterface town,
            Function<BlockPos, State> work,
            Predicate<BlockPos> canClaim
    );

    protected WorkStatusHandle<BlockPos, MCHeldItem> getWorkStatusHandle(TownInterface town) {
        WorkStatusHandle<BlockPos, MCHeldItem> work;
        if (this.specialGlobalRules.contains(SpecialRules.SHARED_WORK_STATUS)) {
            work = town.getWorkStatusHandle(null);
        } else {
            work = town.getWorkStatusHandle(ownerUUID);
        }
        return work;
    }

    protected abstract void tick(
            MCExtra extra,
            WorkStatusHandle<BlockPos, MCHeldItem> workStatus,
            LivingEntity entity,
            Direction facingPos,
            RoomsNeedingVillagerInput<MCRoom, ResourceLocation, BlockPos> roomsNeedingIngredientsOrTools,
            IProductionStatusFactory<STATUS> statusFactory
    );

    protected void setupForGetSupplies(
            TownInterface town,
            BlockPos pos,
            Long currentTick
    ) {
        ContainerTarget.CheckFn<MCTownItem> checkFn = item -> JobsClean.shouldTakeItem(
                journal.getCapacity(), cleanRooms(),
                journal.getItems(), item
        );

        Supplier<ContainerTarget<MCContainer, MCTownItem>> find = () -> {
            Predicate<RoomRecipeMatch<MCRoom>> includeRoom = this::shouldCheckContainerForSupplies;
            List<ContainerTarget<MCContainer, MCTownItem>> chests = Containers.get(
                    town,
                    includeRoom,
                    this::isJobBlock,
                    js -> location.baseRoom().equals(js), false
            );
            List<ContainerTarget<MCContainer, MCTownItem>> closeChests = chests
                    .stream()
                    .sorted(Comparator.comparingDouble(a -> TownContainers.comparison(pos, a)))
                    .toList();
            for (ContainerTarget<MCContainer, MCTownItem> chest : closeChests) {
                if (!chest.hasItem(checkFn)) {
                    continue;
                }
                if (!shouldUseBlockForSupplies(currentTick, chest.getBlockPos())) {
                    continue;
                }
                return chest;
            }
            return null;
        };

        ContainerTarget<MCContainer, MCTownItem> st = find.get();
        if (st != null) {
            this.suppliesTarget = st;
        }
        if (this.suppliesTarget != null) {
            QT.JOB_LOGGER.trace(marker, "Located supplies at {}", this.suppliesTarget.getPosition());
        }
    }

    private boolean shouldUseBlockForSupplies(
            Long currentTick,
            BlockPos b
    ) {
        if (suppliesTarget == null) {
            return true;
        }
        Predicate<Long> isRecent = tick -> isRecentTick(currentTick, tick);
        boolean droppedSuppliesRecently = isRecent.test(lastDropTick) && isRecent.test(secondLastDropTick);
        boolean grabbedSuppliesRecently = grabbedSuppliesRecently(isRecent);
        if (droppedSuppliesRecently && grabbedSuppliesRecently) {
            return !suppliesTarget.getBlockPos().equals(b);
        }
        return true;
    }

    protected boolean isRecentTick(
            Long referenceTick,
            @Nullable Long testedTick
    ) {
        return testedTick != null && testedTick > referenceTick - 50;
    }

    protected abstract boolean grabbedSuppliesRecently(Predicate<Long> isTickRecent);

    protected abstract boolean shouldCheckContainerForSupplies(RoomRecipeMatch<MCRoom> mcRoom);

    protected abstract Collection<? extends Predicate<MCTownItem>> cleanRooms();

    @Override
    public boolean shouldDisappear(
            TownInterface town,
            Vec3 entityPosition
    ) {
        Collection<String> rules = Util.getOrDefault(specialRules, getStatus(), ImmutableList.of());
        for (String rule : rules) {
            if (rule == null) {
                continue;
            }
            if (SpecialRules.REMOVE_FROM_WORLD.equals(rule)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Container getInventory() {
        return inventory;
    }

    @Override
    public ImmutableList<Boolean> getSlotLockStatuses() {
        return this.journal.getSlotLockStatuses();
    }

    @Override
    public void lockSlot(int slotIndex) {

    }

    @Override
    public void unlockSlot(int slotIndex) {

    }

    @Override
    public DataSlot getLockSlot(int i) {
        return locks.get(i);
    }

    @Override
    public void initializeItems(Iterable<MCHeldItem> mcTownItemStream) {
        journal.setItems(mcTownItemStream);
    }

    @Override
    public SNAPSHOT getJournalSnapshot() {
        return journal.getSnapshot();
    }

    @Override
    public void initialize(
            ServerLevel level,
            Snapshot<MCHeldItem> journal
    ) {
        this.journal.initialize((SNAPSHOT) journal);
    }

    @Override
    public boolean isInitialized() {
        return this.journal.isInitialized();
    }

    @Override
    public boolean shouldBeNoClip(
            TownInterface town,
            BlockPos blockPos
    ) {
        return false;
    }

    @Override
    public boolean addToEmptySlot(MCHeldItem i) {
//        boolean isAllowedToPickUp = allowedToPickUp.contains(i.get());
//        if (!isAllowedToPickUp) {
//            return false;
//        }
        return journal.addItemIfSlotAvailable(i);
    }

    @Override
    public void containerChanged(Container p_18983_) {
        if (Jobs.isUnchanged(p_18983_, journal.getItems())) {
            return;
        }

        ImmutableList.Builder<MCHeldItem> b = ImmutableList.builder();

        for (int i = 0; i < p_18983_.getContainerSize(); i++) {
            ItemStack item = p_18983_.getItem(i);
            MCHeldItem mcHeldItem = MCHeldItem.fromMCItemStack(item);
            if (locks.get(i)
                     .get() == 1) {
                mcHeldItem = mcHeldItem.locked();
            }
            b.add(mcHeldItem);
        }
        journal.setItemsNoUpdateNoCheck(b.build());
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
                ImmutableList<Predicate<MCTownItem>> allFillableRecipes = ImmutableList.copyOf(
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

    @Override
    public boolean canStopWorkingAtAnyTime() {
        STATUS status = getStatus();
        ImmutableList<Supplier<Boolean>> importantStauses = ImmutableList.of(
                status::isExtractingProduct,
                status::isWaitingForTimers
        );
        boolean mustKeepWorking = importantStauses.stream()
                                                  .anyMatch(Supplier::get);
        return !mustKeepWorking;
    }

    public interface TestFn<S, I> {
        boolean test(
                Map<S, Boolean> canUseIngredientsForWork,
                I item
        );

        boolean testAssumeNeeded(
                I item
        );
    }
}
