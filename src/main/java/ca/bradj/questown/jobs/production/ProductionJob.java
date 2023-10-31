package ca.bradj.questown.jobs.production;

import ca.bradj.questown.QT;
import ca.bradj.questown.integration.minecraft.MCContainer;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.*;
import ca.bradj.questown.mobs.visitor.ContainerTarget;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableList;
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
import java.util.stream.Collectors;

public abstract class ProductionJob<
        STATUS extends IProductionStatus<STATUS>,
        SNAPSHOT extends Snapshot<MCHeldItem>,
        JOURNAL extends Journal<STATUS, MCHeldItem, SNAPSHOT>
    > implements Job<MCHeldItem, SNAPSHOT, STATUS>, LockSlotHaver, ContainerListener, JournalItemsListener<MCHeldItem>, Jobs.LootDropper<MCHeldItem>, Jobs.ContainerItemTaker {

    private final Marker marker;

    private final ArrayList<DataSlot> locks = new ArrayList<>();
    protected final Container inventory;
    protected final JOURNAL journal;
    private final IProductionStatusFactory<STATUS> statusFactory;
    private final int maxState;
    private ContainerTarget<MCContainer, MCTownItem> successTarget;
    private ContainerTarget<MCContainer, MCTownItem> suppliesTarget;
    private boolean dropping;

    // TODO: Support more recipes
    protected final RecipeProvider recipe;
    private final ImmutableList<MCTownItem> allowedToPickUp;

    private final UUID ownerUUID;
    private Map<Integer, ? extends Collection<MCRoom>> roomsNeedingIngredients;

    public interface RecipeProvider {
        ImmutableList<JobsClean.TestFn<MCTownItem>> getRecipe(int workState);
    }

    public ProductionJob(
            UUID ownerUUID,
            int inventoryCapacity,
            int maxState,
            ImmutableList<MCTownItem> allowedToPickUp,
            RecipeProvider recipe,
            Marker logMarker,
            IProductionStatusFactory<STATUS> sFac
    ) {
        // TODO: This is copy pasted. Reduce duplication.
        SimpleContainer sc = new SimpleContainer(inventoryCapacity) {
            @Override
            public int getMaxStackSize() {
                return 1;
            }
        };
        this.ownerUUID = ownerUUID;
        this.allowedToPickUp = allowedToPickUp;
        this.maxState = maxState;
        this.marker = logMarker;
        this.recipe = recipe;
        this.inventory = sc;
        sc.addListener(this);

        for (int i = 0; i < inventoryCapacity; i++) {
            this.locks.add(new LockSlot(i, this));
        }

        this.journal = getInitializedJournal(inventoryCapacity, sFac);
        this.journal.addItemListener(this);

        this.statusFactory = sFac;
    }

    protected abstract JOURNAL getInitializedJournal(
            int inventoryCapacity,  IStatusFactory<STATUS> sFac
    );

    @Override
    public void addStatusListener(StatusListener o) {
        this.journal.addStatusListener(o);
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

    @Override
    public void addItem(MCHeldItem mcHeldItem) {
        journal.addItem(mcHeldItem);
    }

    @Override
    public boolean isInventoryFull() {
        return journal.isInventoryFull();
    }

    protected abstract Map<Integer, Boolean> getSupplyItemStatus();

    protected void tryDropLoot(
            BlockPos entityPos
    ) {
        if (!journal.getStatus().isDroppingLoot()) {
            return;
        }
        if (this.dropping) {
            QT.JOB_LOGGER.debug(marker, "Trying to drop too quickly");
        }
        this.dropping = Jobs.tryDropLoot(this, entityPos, successTarget);
    }

    protected void tryGetSupplies(
            JobTownProvider<MCRoom> town,
            BlockPos entityPos
    ) {
        // TODO: Introduce this status for farmer
        STATUS status = journal.getStatus();
        if (!status.isCollectingSupplies()) {
            return;
        }

        Optional<Integer> first = roomsNeedingIngredients.entrySet()
                .stream()
                .filter(v -> !v.getValue().isEmpty())
                .map(Map.Entry::getKey)
                .findFirst();

        if (first.isEmpty()) {
            QT.JOB_LOGGER.warn("Trying to try container items when no rooms need items");
            return;
        }

        Jobs.tryTakeContainerItems(
                this, entityPos, suppliesTarget,
                item -> JobsClean.shouldTakeItem(
                        journal.getCapacity(),
                        recipe.getRecipe(first.get()),
                        journal.getItems(), item
                )
        );
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
            return findJobSite(town);
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

        return findNonWorkTarget(entityBlockPos, entityPos, town);
    }

    protected abstract BlockPos findNonWorkTarget(
            BlockPos entityBlockPos,
            Vec3 entityPos,
            TownInterface town
    );

    protected abstract BlockPos findProductionSpot(ServerLevel level);

    protected abstract BlockPos findJobSite(TownInterface town);

    protected abstract Map<Integer, ? extends Collection<MCRoom>> roomsNeedingIngredientsOrTools(TownInterface town);

    @Override
    public void tick(
            TownInterface town,
            LivingEntity entity,
            Direction facingPos
    ) {
        this.roomsNeedingIngredients = roomsNeedingIngredientsOrTools(town);
        this.tick(town, entity, facingPos, roomsNeedingIngredients, statusFactory);
    }

    protected abstract void tick(
            TownInterface town,
            LivingEntity entity,
            Direction facingPos,
            Map<Integer, ? extends Collection<MCRoom>> roomsNeedingIngredients,
            IProductionStatusFactory<STATUS> statusFactory
    );

    private void setupForGetSupplies(
            TownInterface town
    ) {
        QT.JOB_LOGGER.debug(marker, "Searching for supplies");
        ContainerTarget.CheckFn<MCTownItem> checkFn = item -> JobsClean.shouldTakeItem(
                journal.getCapacity(), convertToCleanFns(roomsNeedingIngredients),
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
            QT.JOB_LOGGER.debug(marker, "Baker located supplies at {}", this.suppliesTarget.getPosition());
        }
    }

    @Override
    public boolean shouldDisappear(
            TownInterface town,
            Vec3 entityPosition
    ) {
        // Since production workers don't leave town. They don't need to disappear.
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
    public void initialize(SNAPSHOT journal) {
        this.journal.initialize(journal);
    }


    @Override
    public boolean shouldBeNoClip(
            TownInterface town,
            BlockPos blockPos
    ) {
        return false;
    }

    @Override
    public boolean addToEmptySlot(MCTownItem mcTownItem) {
        boolean isAllowedToPickUp = allowedToPickUp.contains(mcTownItem);
        if (!isAllowedToPickUp) {
            return false;
        }
        return journal.addItemIfSlotAvailable(new MCHeldItem(mcTownItem));
    }

    @Override
    public void containerChanged(Container p_18983_) {
        if (Jobs.isUnchanged(p_18983_, journal.getItems())) {
            return;
        }

        ImmutableList.Builder<MCHeldItem> b = ImmutableList.builder();

        for (int i = 0; i < p_18983_.getContainerSize(); i++) {
            ItemStack item = p_18983_.getItem(i);
            b.add(new MCHeldItem(MCTownItem.fromMCItemStack(item), locks.get(i).get() == 1));
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

                Set<Integer> statesToFeed = roomsNeedingIngredients.entrySet().stream().filter(
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
