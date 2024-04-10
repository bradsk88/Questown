package ca.bradj.questown.jobs.production;

import ca.bradj.questown.QT;
import ca.bradj.questown.integration.minecraft.MCContainer;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.*;
import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.mc.MCRoomWithBlocks;
import ca.bradj.questown.mc.Util;
import ca.bradj.questown.town.AbstractWorkStatusStore;
import ca.bradj.questown.town.Claim;
import ca.bradj.questown.town.interfaces.ContainerRoomFinder;
import ca.bradj.questown.town.interfaces.RoomsHolder;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.interfaces.WorkStatusHandle;
import ca.bradj.roomrecipes.adapter.Positions;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerListener;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.Marker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static ca.bradj.questown.jobs.Jobs.isCloseTo;

/**
 * @param <STATUS>
 * @param <SNAPSHOT>
 * @param <JOURNAL>
 * @deprecated Use DeclarativeJob
 */
public abstract class ProductionJob<STATUS extends IProductionStatus<STATUS>, SNAPSHOT extends Snapshot<MCHeldItem>, JOURNAL extends Journal<STATUS, MCHeldItem, SNAPSHOT>> implements
        Job<MCHeldItem, SNAPSHOT, STATUS>, LockSlotHaver, ContainerListener, JournalItemsListener<MCHeldItem>,
        Jobs.LootDropper<MCHeldItem>, SignalSource {

    private final Marker marker;

    private final ArrayList<DataSlot> locks = new ArrayList<>();
    protected final Container inventory;
    protected final JOURNAL journal;
    private final IProductionStatusFactory<STATUS> statusFactory;
    private final Supplier<Claim> claimSupplier;
    private ContainerTarget<MCContainer, MCTownItem> successTarget;
    protected ContainerTarget<MCContainer, MCTownItem> suppliesTarget;
    private boolean dropping;

    protected final RecipeProvider recipe;
    private final ImmutableList<MCTownItem> allowedToPickUp;

    protected final UUID ownerUUID;

    public final Function<STATUS, ? extends @NotNull Collection<String>> specialRules;
    protected final ImmutableList<String> specialGlobalRules;
    protected @Nullable BlockPos lookTarget;

    public BlockPos getJobSite(
            TownInterface town
    ) {
        if (this.jobSite == null) {
            ServerLevel sl = town.getServerLevel();
            this.jobSite = findJobSite(
                    town.getRoomHandle(),
                    specialRoomsSupplier(town),
                    getWorkStatusHandle(town)::getJobBlockState,
                    sl::isEmptyBlock,
                    sl.getRandom()
            );
        }
        return jobSite;
    }

    @NotNull
    private Supplier<Collection<MCRoomWithBlocks>> specialRoomsSupplier(TownInterface town) {
        return () -> MCRoomWithBlocks.fromRooms(
                bp -> town.getServerLevel().getBlockState(bp).getBlock(),
                getRoomsWhereSpecialRulesApply(town.getRoomHandle(), town.getTownData()).values().stream()
                                                                                        .flatMap(Collection::stream)
                                                                                        .toList()
        );
    }

    private BlockPos jobSite;

    protected abstract Map<STATUS, ImmutableList<MCRoom>> getRoomsWhereSpecialRulesApply(
            ContainerRoomFinder<MCRoom, MCTownItem> rooms,
            WorksBehaviour.TownData townData
    );

    @Override
    public abstract Signals getSignal();

    public interface RecipeProvider {
        ImmutableList<JobsClean.TestFn<MCTownItem>> getRecipe(int workState);
    }

    public ProductionJob(
            UUID ownerUUID,
            int inventoryCapacity,
            ImmutableList<MCTownItem> allowedToPickUp,
            RecipeProvider recipe,
            Marker logMarker,
            BiFunction<Integer, SignalSource, JOURNAL> journalInit,
            IProductionStatusFactory<STATUS> sFac,
            ImmutableMap<STATUS, ? extends Collection<String>> specialStatusRules,
            ImmutableList<String> specialGlobalRules,
            Supplier<Claim> claimSupplier
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
        this.allowedToPickUp = allowedToPickUp;
        this.marker = logMarker;
        this.recipe = recipe;
        this.inventory = sc;
        sc.addListener(this);

        for (int i = 0; i < inventoryCapacity; i++) {
            this.locks.add(new LockSlot(i, this));
        }

        this.journal = journalInit.apply(inventoryCapacity, this);
        this.journal.addItemListener(this);

        this.statusFactory = sFac;

        this.specialRules = k -> Util.getOrDefaultCollection(specialStatusRules, k, ImmutableList.of());
        this.claimSupplier = claimSupplier;
    }

    protected ItemsHolder<MCHeldItem> asItemsHolder() {
        return journal;
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
    public STATUS getStatus() {
        return computeStatus();
    }

    @Override
    public String getStatusToSyncToClient() {
        return this.computeStatus().nameV2();
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
    public boolean hasAnyLootToDrop() {
        return journal.hasAnyLootToDrop();
    }

    @Override
    public Iterable<MCHeldItem> getItems() {
        return journal.getItems();
    }

    @Override
    public boolean removeItem(MCHeldItem mct) {
        return journal.removeItem(mct);
    }

    protected abstract Map<STATUS, Boolean> getSupplyItemStatus();

    protected void tryDropLoot(
            BlockPos entityPos
    ) {
        if (successTarget == null) {
            return;
        }
        if (!isCloseTo(entityPos, Positions.ToBlock(successTarget.getPosition(), successTarget.getYPosition()))) {
            return;
        }
        if (!computeStatus().isDroppingLoot()) {
            return;
        }
        if (this.dropping) {
            QT.JOB_LOGGER.debug(marker, "Trying to drop too quickly");
        }
        this.dropping = Jobs.tryDropLoot(this, entityPos, successTarget);
    }

    @NotNull
    protected ImmutableList<Predicate<MCTownItem>> convertToCleanFns(
            Map<STATUS, ? extends Collection<MCRoom>> statusMap
    ) {
        // TODO: Be smarter? We're just finding the first room that needs stuff.
        Optional<STATUS> first = statusMap.entrySet().stream().filter(v -> !v.getValue().isEmpty())
                                          .map(Map.Entry::getKey).findFirst();

        if (first.isEmpty()) {
            return ImmutableList.of();
        }

        return ImmutableList.copyOf(recipe.getRecipe(first.get().value()).stream()
                                          .map(v -> (Predicate<MCTownItem>) v::test)
                                          .toList());
    }

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

        STATUS status = computeStatus();
        if (status.isGoingToJobsite()) {
            BlockPos jobSite1 = getJobSite(town);
            this.setLookTarget(jobSite1);
            return jobSite1;
        }

        if (status.isWorkingOnProduction()) {
            @Nullable WorkSpot<?, BlockPos> productionSpot = findProductionSpot(sl);
            if (productionSpot != null) {
                this.setLookTarget(productionSpot.position());
                return productionSpot.interactionSpot();
            }
        }

        if (status.isDroppingLoot()) {
            successTarget = Jobs.setupForDropLoot(town, this.successTarget);
            if (successTarget != null) {
                this.setLookTarget(Positions.ToBlock(successTarget.getPosition(), successTarget.getYPosition()));
                return Positions.ToBlock(successTarget.getInteractPosition(), successTarget.getYPosition());
            }
        }

        if (computeStatus().isCollectingSupplies()) {
            setupForGetSupplies(town);
            if (suppliesTarget != null) {
                this.setLookTarget(Positions.ToBlock(suppliesTarget.getPosition(), suppliesTarget.getYPosition()));
                return Positions.ToBlock(suppliesTarget.getInteractPosition(), suppliesTarget.getYPosition());
            }
        }

        if (shouldDisappear(town, entityPos)) {
            return entityBlockPos;
        }

        return findNonWorkTarget(entityBlockPos, entityPos, town);
    }

    protected STATUS computeStatus() {
        STATUS statusFromTown = journal.getStatus();
        if (specialRules.apply(statusFromTown).contains(SpecialRules.FORCE_DROP_LOOT)) {
            return statusFactory.droppingLoot();
        }
        return statusFromTown;
    }

    protected void setLookTarget(BlockPos jobSite1) {
        this.lookTarget = jobSite1;
    }

    protected abstract BlockPos findNonWorkTarget(
            BlockPos entityBlockPos,
            Vec3 entityPos,
            TownInterface town
    );

    protected abstract @Nullable WorkSpot<?, BlockPos> findProductionSpot(ServerLevel level);

    protected abstract BlockPos findJobSite(
            RoomsHolder town,
            Supplier<? extends Collection<MCRoomWithBlocks>> roomsWhereSpecialRulesApply,
            Function<BlockPos, AbstractWorkStatusStore.State> work,
            Predicate<BlockPos> isEmpty,
            Random rand
    );

    private ControlledCache<Map<STATUS, Collection<MCRoom>>> roomsNeedingIngredientsOrTools;

    protected abstract Map<STATUS, Collection<MCRoom>> roomsNeedingIngredientsOrTools(
            TownInterface town,
            Function<BlockPos, AbstractWorkStatusStore.State> work,
            Predicate<BlockPos> canClaim
    );

    @Override
    public void tick(
            TownInterface town,
            LivingEntity entity,
            Direction facingPos
    ) {
        WorkStatusHandle<BlockPos, MCHeldItem> work = getWorkStatusHandle(town);

        this.roomsNeedingIngredientsOrTools = new ControlledCache<>(() -> roomsNeedingIngredientsOrTools(
                town,
                work::getJobBlockState,
                (BlockPos bp) -> work.canClaim(bp, this.claimSupplier)
        ));

        this.tick(town, work, entity, facingPos, roomsNeedingIngredientsOrTools, statusFactory);
    }

    private WorkStatusHandle<BlockPos, MCHeldItem> getWorkStatusHandle(TownInterface town) {
        WorkStatusHandle<BlockPos, MCHeldItem> work;
        if (this.specialGlobalRules.contains(SpecialRules.SHARED_WORK_STATUS)) {
            work = town.getWorkStatusHandle(null);
        } else {
            work = town.getWorkStatusHandle(ownerUUID);
        }
        return work;
    }

    protected abstract void tick(
            TownInterface town,
            WorkStatusHandle<BlockPos, MCHeldItem> workStatus,
            LivingEntity entity,
            Direction facingPos,
            ControlledCache<Map<STATUS, Collection<MCRoom>>> roomsNeedingIngredientsOrTools,
            IProductionStatusFactory<STATUS> statusFactory
    );

    protected abstract ImmutableList<STATUS> sortByPriority(Collection<STATUS> states);

    private void setupForGetSupplies(
            TownInterface town
    ) {
        ContainerTarget.CheckFn<MCTownItem> originalCheck = item -> JobsClean.shouldTakeItem(
                journal.getCapacity(),
                convertToCleanFns(roomsNeedingIngredientsOrTools.get(town.getDebugHandle().isCacheEnabled())),
                journal.getItems(),
                item
        );
        ContainerTarget.CheckFn<MCTownItem> checkFn = originalCheck;
        for (STATUS i : sortByPriority(roomsNeedingIngredientsOrTools.get(town.getDebugHandle().isCacheEnabled())
                                                                     .keySet())) {
            if (specialRules.apply(i).contains(SpecialRules.INGREDIENT_ANY_VALID_WORK_OUTPUT)) {
                Predicate<MCTownItem> isAnyWorkResult = item -> Works.isWorkResult(town.getTownData(), item);
                checkFn = item -> JobsClean.shouldTakeItem(
                        journal.getCapacity(),
                        ImmutableList.of(itum -> isAnyWorkResult.test(itum) || originalCheck.Matches(itum)),
                        journal.getItems(),
                        item
                );
                break;
            }
        }

        if (this.suppliesTarget != null) {
            if (!this.suppliesTarget.hasItem(checkFn)) {
                this.suppliesTarget = town.findMatchingContainer(checkFn);
            }
        } else {
            this.suppliesTarget = town.findMatchingContainer(checkFn);
        }
        if (this.suppliesTarget != null) {
            QT.JOB_LOGGER.trace(marker, "Located supplies at {}", this.suppliesTarget.getPosition());
        }
    }

    @Override
    public boolean shouldDisappear(
            TownInterface town,
            Vec3 entityPosition
    ) {
        Collection<String> rules = specialRules.apply(getStatus());
        if (rules == null) {
            return false;
        }
        return rules.contains(SpecialRules.REMOVE_FROM_WORLD);
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
    public void initialize(Snapshot<MCHeldItem> journal) {
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
        boolean isAllowedToPickUp = allowedToPickUp.contains(i.get());
        if (!isAllowedToPickUp) {
            return false;
        }
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
            if (locks.get(i).get() == 1) {
                mcHeldItem = mcHeldItem.locked();
            }
            b.add(mcHeldItem);
        }
        journal.setItemsNoUpdateNoCheck(b.build());
    }

    protected EntityInvStateProvider<STATUS> defaultEntityInvProvider(WorksBehaviour.TownData townData) {
        return new EntityInvStateProvider<>() {
            @Override
            public boolean inventoryFull() {
                return journal.isInventoryFull();
            }

            @Override
            public boolean hasNonSupplyItems(boolean allowCaching) {

                Set<STATUS> statesToFeed = roomsNeedingIngredientsOrTools.get(allowCaching).entrySet().stream()
                                                                         .filter(v -> !v.getValue().isEmpty())
                                                                         .map(Map.Entry::getKey)
                                                                         .collect(Collectors.toSet());
                ImmutableList.Builder<JobsClean.TestFn<MCTownItem>> b = ImmutableList.builder();
                statesToFeed.stream()
                            .flatMap(
                                    v -> recipe.getRecipe(v.value())
                                               .stream())
                            .forEach(b::add);
                Collection<String> stateRules = specialRules.apply(statusFactory.fromJobBlockState(0));
                if (stateRules.contains(SpecialRules.INGREDIENT_ANY_VALID_WORK_OUTPUT)) {
                    b.add(i -> Works.isWorkResult(townData, i));
                }
                return Jobs.hasNonSupplyItems(journal, b.build());
            }

            @Override
            public Map<STATUS, Boolean> getSupplyItemStatus() {
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
        boolean mustKeepWorking = importantStauses.stream().anyMatch(Supplier::get);
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
