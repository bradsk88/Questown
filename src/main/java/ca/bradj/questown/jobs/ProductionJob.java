package ca.bradj.questown.jobs;

import ca.bradj.questown.QT;
import ca.bradj.questown.gui.InventoryAndStatusMenu;
import ca.bradj.questown.gui.TownQuestsContainer;
import ca.bradj.questown.integration.minecraft.MCContainer;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.mobs.visitor.ContainerTarget;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.interfaces.TownInterface;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerListener;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.Marker;
import org.jetbrains.annotations.Nullable;
import org.stringtemplate.v4.ST;

import java.util.*;

public abstract class ProductionJob<STATUS extends IStatus<STATUS>, SNAPSHOT extends Snapshot<MCHeldItem>> implements Job<MCHeldItem, SNAPSHOT, STATUS>, LockSlotHaver, ContainerListener, JournalItemsListener<MCHeldItem>, Jobs.LootDropper<MCHeldItem>, Jobs.ContainerItemTaker {

    private final Marker marker;

    private final ArrayList<DataSlot> locks = new ArrayList<>();
    private final Container inventory;
    private final Journal<STATUS, MCHeldItem> journal;
    private ContainerTarget<MCContainer, MCTownItem> successTarget;
    private ContainerTarget<MCContainer, MCTownItem> suppliesTarget;
    private boolean dropping;

    // TODO: Support more recipes
    private final ImmutableList<JobsClean.TestFn<MCTownItem>> recipe;
    private final ImmutableList<MCTownItem> allowedToPickUp;

    private final UUID ownerUUID;

    public ProductionJob(
            UUID ownerUUID,
            int inventoryCapacity,
            ImmutableList<MCTownItem> allowedToPickUp,
            ImmutableList<JobsClean.TestFn<MCTownItem>> recipe,
            Marker logMarker
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
        this.marker = logMarker;
        this.recipe = recipe;
        this.inventory = sc;
        sc.addListener(this);

        for (int i = 0; i < inventoryCapacity; i++) {
            this.locks.add(new LockSlot(i, this));
        }

        this.journal = initializeJournal(inventoryCapacity);
        this.journal.addItemListener(this);
    }

    protected abstract Journal<STATUS, MCHeldItem> initializeJournal(int inventoryCapacity);

    @Override
    public void addStatusListener(StatusListener<STATUS> o) {
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

    protected abstract Map<STATUS, Boolean> getSupplyItemStatus();

    private void tryDropLoot(
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

    private void tryGetSupplies(
            BlockPos entityPos
    ) {
        // TODO: Introduce this status for farmer
        STATUS status = journal.getStatus();
        if (!status.isCollectingSupplies()) {
            return;
        }
        Jobs.tryTakeContainerItems(
                this, entityPos, suppliesTarget,
                item -> JobsClean.shouldTakeItem(journal.getCapacity(), recipe, journal.getItems(), item)
        );
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
            return goToJobSite();
        }

        if (status.isWorkingOnProduction()) {
            return goToProductionSpot();
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

        return getNonWorkTarget(entityBlockPos, entityPos, town);
    }

    protected abstract BlockPos getNonWorkTarget(
            BlockPos entityBlockPos,
            Vec3 entityPos,
            TownInterface town
    );

    protected abstract BlockPos goToProductionSpot();

    protected abstract BlockPos goToJobSite();

    private void setupForGetSupplies(
            TownInterface town
    ) {
        QT.JOB_LOGGER.debug(marker, "Baker is searching for supplies");
        ContainerTarget.CheckFn<MCTownItem> checkFn = item -> JobsClean.shouldTakeItem(
                journal.getCapacity(), recipe, journal.getItems(), item
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
    public boolean shouldBeNoClip(
            TownInterface town,
            BlockPos blockPos
    ) {
        return false;
    }

    @Override
    public TranslatableComponent getJobName() {
        return new TranslatableComponent("jobs.baker");
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

}
