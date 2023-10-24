package ca.bradj.questown.jobs;

import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableList;

// FIXME: Implement
public class DeclarativeJournal implements Journal<ProductionStatus, MCHeldItem, SimpleSnapshot<ProductionStatus, MCHeldItem>> {
    public DeclarativeJournal(
            SignalSource o,
            int inventoryCapacity,
            EmptyFactory<MCHeldItem> air
    ) {

    }

    @Override
    public void addItemListener(JournalItemsListener<MCHeldItem> l) {

    }

    @Override
    public int getCapacity() {
        return 0;
    }

    @Override
    public boolean hasAnyLootToDrop() {
        return false;
    }

    @Override
    public ImmutableList<MCHeldItem> getItems() {
        return null;
    }

    @Override
    public boolean removeItem(MCHeldItem mct) {
        return false;
    }

    @Override
    public void addItem(MCHeldItem mcHeldItem) {

    }

    @Override
    public boolean isInventoryFull() {
        return false;
    }

    @Override
    public void setItems(Iterable<MCHeldItem> mcTownItemStream) {

    }

    @Override
    public boolean addItemIfSlotAvailable(MCHeldItem mcHeldItem) {
        return false;
    }

    @Override
    public void setItemsNoUpdateNoCheck(ImmutableList<MCHeldItem> build) {

    }

    @Override
    public ProductionStatus getStatus() {
        return null;
    }

    @Override
    public void addStatusListener(StatusListener o) {

    }

    @Override
    public void initializeStatus(ProductionStatus s) {

    }

    @Override
    public ImmutableList<Boolean> getSlotLockStatuses() {
        return null;
    }

    @Override
    public SimpleSnapshot<ProductionStatus, MCHeldItem> getSnapshot() {
        return null;
    }

    @Override
    public void initialize(SimpleSnapshot<ProductionStatus, MCHeldItem> journal) {

    }

    @Override
    public void setItem(
            int idx,
            MCHeldItem mcHeldItem
    ) {

    }

    public void tick(
            JobTownProvider<ProductionStatus, MCRoom> jtp,
            EntityLocStateProvider<MCRoom> elp,
            EntityInvStateProvider<ProductionStatus> productionStatusEntityInvStateProvider
    ) {

    }
}
