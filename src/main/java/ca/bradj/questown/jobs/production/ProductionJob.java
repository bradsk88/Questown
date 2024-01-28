package ca.bradj.questown.jobs.production;

import ca.bradj.questown.QT;
import ca.bradj.questown.integration.minecraft.MCContainer;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.*;
import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.town.AbstractWorkStatusStore;
import ca.bradj.questown.town.Claim;
import ca.bradj.questown.town.interfaces.RoomsHolder;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.interfaces.WorkStatusHandle;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
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

public abstract class ProductionJob<
        STATUS extends IProductionStatus<STATUS>,
        SNAPSHOT extends Snapshot<MCHeldItem>,
        JOURNAL extends Journal<STATUS, MCHeldItem, SNAPSHOT>
        > implements Job<MCHeldItem, SNAPSHOT, STATUS>, LockSlotHaver, ContainerListener, JournalItemsListener<MCHeldItem>, Jobs.LootDropper<MCHeldItem>, SignalSource {

    private final Marker marker;

    private final ArrayList<DataSlot> locks = new ArrayList<>();
    protected final Container inventory;
    protected final JOURNAL journal;
    private final IProductionStatusFactory<STATUS> statusFactory;
    private final Supplier<Claim> claimSupplier;
    private ContainerTarget<MCContainer, MCTownItem> successTarget;
    protected ContainerTarget<MCContainer, MCTownItem> suppliesTarget;
    private boolean dropping;

    // TODO: Support more recipes
    protected final RecipeProvider recipe;
    private final ImmutableList<MCTownItem> allowedToPickUp;

    protected final UUID ownerUUID;
    private Map<Integer, Collection<MCRoom>> roomsNeedingIngredientsOrTools;

    public final ImmutableMap<STATUS, String> specialRules;
    protected final ImmutableList<String> specialGlobalRules;
    private BlockPos jobSite;

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
            ImmutableMap<STATUS, String> specialRules,
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

        this.specialRules = specialRules;
        this.claimSupplier = claimSupplier;
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
        return journal.getStatus();
    }

    @Override
    public String getStatusToSyncToClient() {
        return this.journal.getStatus().name();
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

    protected abstract Map<Integer, Boolean> getSupplyItemStatus();

    protected void tryDropLoot(
            BlockPos entityPos
    ) {
        if (successTarget == null) {
            return;
        }
        if (!isCloseTo(entityPos, successTarget.getBlockPos())) {
            return;
        }
        if (!journal.getStatus().isDroppingLoot()) {
            return;
        }
        if (this.dropping) {
            QT.JOB_LOGGER.debug(marker, "Trying to drop too quickly");
        }
        this.dropping = Jobs.tryDropLoot(this, entityPos, successTarget);
    }

    @NotNull
    protected ImmutableList<JobsClean.TestFn<MCTownItem>> convertToCleanFns(
            Map<Integer, ? extends Collection<MCRoom>> statusMap
    ) {
        // TODO: Be smarter? We're just finding the first room that needs stuff.
        Optional<Integer> first = statusMap.entrySet()
                .stream()
                .filter(v -> !v.getValue().isEmpty())
                .map(Map.Entry::getKey)
                .findFirst();

        if (first.isEmpty()) {
            return ImmutableList.of();
        }

        return recipe.getRecipe(first.get());
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
        if (status.isGoingToJobsite()) {
            if (this.jobSite == null) {
                this.jobSite = findJobSite(
                        town.getRoomHandle(),
                        getWorkStatusHandle(town)::getJobBlockState,
                        sl::isEmptyBlock,
                        sl.getRandom()
                );
            }
            return jobSite;
        }

        if (status.isWorkingOnProduction()) {
            return findProductionSpot(sl);
        }

        if (status.isDroppingLoot()) {
            successTarget = Jobs.setupForDropLoot(town, this.successTarget);
            if (successTarget != null) {
                return successTarget.getBlockPos();
            }
        }

        if (journal.getStatus().isCollectingSupplies()) {
            setupForGetSupplies(town);
            if (suppliesTarget != null) {
                return suppliesTarget.getBlockPos();
            }
        }

        if (shouldDisappear(town, entityPos)) {
            return entityBlockPos;
        }

        return findNonWorkTarget(entityBlockPos, entityPos, town);
    }

    protected abstract BlockPos findNonWorkTarget(
            BlockPos entityBlockPos,
            Vec3 entityPos,
            TownInterface town
    );

    protected abstract BlockPos findProductionSpot(ServerLevel level);

    protected abstract BlockPos findJobSite(
            RoomsHolder town,
            Function<BlockPos, AbstractWorkStatusStore.State> work,
            Predicate<BlockPos> isEmpty,
            RandomSource rand
    );

    protected abstract Map<Integer, Collection<MCRoom>> roomsNeedingIngredientsOrTools(
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
        this.roomsNeedingIngredientsOrTools = roomsNeedingIngredientsOrTools(
                town, work::getJobBlockState, (BlockPos bp) -> work.canClaim(bp, this.claimSupplier)
        );

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
            Map<Integer, Collection<MCRoom>> roomsNeedingIngredientsOrTools,
            IProductionStatusFactory<STATUS> statusFactory
    );

    private void setupForGetSupplies(
            TownInterface town
    ) {
        QT.JOB_LOGGER.debug(marker, "Searching for supplies");
        ContainerTarget.CheckFn<MCTownItem> checkFn = item -> JobsClean.shouldTakeItem(
                journal.getCapacity(), convertToCleanFns(roomsNeedingIngredientsOrTools),
                journal.getItems(), item
        );
        if (this.suppliesTarget != null) {
            if (!this.suppliesTarget.hasItem(
                    checkFn
            )) {
                this.suppliesTarget = town.findMatchingContainer(
                        checkFn
                );
            }
        } else {
            this.suppliesTarget = town.findMatchingContainer(
                    checkFn
            );
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
        String rule = specialRules.get(getStatus());
        if (rule == null) {
            return false;
        }
        return SpecialRules.REMOVE_FROM_WORLD.equals(rule);
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

    protected EntityInvStateProvider<Integer> defaultEntityInvProvider() {
        return new EntityInvStateProvider<>() {
            @Override
            public boolean inventoryFull() {
                return journal.isInventoryFull();
            }

            @Override
            public boolean hasNonSupplyItems() {

                Set<Integer> statesToFeed = roomsNeedingIngredientsOrTools.entrySet().stream().filter(
                        v -> !v.getValue().isEmpty()
                ).map(Map.Entry::getKey).collect(Collectors.toSet());
                ImmutableList<JobsClean.TestFn<MCTownItem>> allFillableRecipes = ImmutableList.copyOf(
                        statesToFeed.stream().flatMap(v -> recipe.getRecipe(v).stream()).toList()
                );
                return Jobs.hasNonSupplyItems(journal, allFillableRecipes);
            }

            @Override
            public Map<Integer, Boolean> getSupplyItemStatus() {
                return ProductionJob.this.getSupplyItemStatus();
            }
        };
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
