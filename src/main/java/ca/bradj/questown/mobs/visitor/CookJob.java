package ca.bradj.questown.mobs.visitor;

import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.*;
import ca.bradj.questown.town.interfaces.TownInterface;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerListener;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

public class CookJob implements Job<MCHeldItem, CookJournal.Snapshot<MCHeldItem>>, LockSlotHaver, ContainerListener {
    private final ArrayList<DataSlot> locks = new ArrayList<>();
    private ArrayList<StatusListener> statusListeners = new ArrayList<>();
    private final Container inventory;
    private Signals signal;
    private CookJournal<MCTownItem, MCHeldItem> journal;

    public CookJob(
            @Nullable ServerLevel level,
            int inventoryCapacity
    ) {
        // TODO: This is copy pasted. Reduce duplication.
        SimpleContainer sc = new SimpleContainer(inventoryCapacity) {
            @Override
            public int getMaxStackSize() {
                return 1;
            }
        };
        this.inventory = sc;
        sc.addListener(this);

        for (int i = 0; i < inventoryCapacity; i++) {
            this.locks.add(new LockSlot(i, this));
        }

        this.journal = new CookJournal<>(inventoryCapacity);
    }

    @Override
    public void addStatusListener(StatusListener o) {
        this.statusListeners.add(o);
    }

    @Override
    public GathererJournal.Status getStatus() {
        // TODO: Implement different statuses
        return GathererJournal.Status.IDLE;
    }

    @Override
    public void initializeStatus(GathererJournal.Status s) {
        // TODO: Implement
        return;
    }

    @Override
    public void tick(
            TownInterface town,
            BlockPos entityPos
    ) {
        if (town == null || town.getServerLevel() == null) {
            return;
        }
        processSignal(town.getServerLevel(), this);

        // TODO: Implement all of this
//        // TODO: Take cooked food to chests
//        if (successTarget != null && !successTarget.isStillValid()) {
//            successTarget = null;
//        }
//
//        // TODO: Get food for self to eat
//        if (foodTarget != null && !foodTarget.isStillValid()) {
//            foodTarget = null;
//        }
//
//        if (jobBlockTarget != null) {
//            if (brain.getOrSetKitchenLocation(entityPos) == null) {
//                jobBlockTarget = null;
//            }
//        }
//        tryDropLoot(entityPos);
//        tryTakeFood(entityPos);
    }


    private static void processSignal(
            Level level,
            CookJob e
    ) {
        if (level.isClientSide()) {
            return;
        }

        /*
         * Sunrise: 22000
         * Dawn: 0
         * Noon: 6000
         * Evening: 11500
         */

        e.signal = Signals.fromGameTime(level.getDayTime());
        // TODO: Tick the journal
//        e.journal.tick(e);
    }

    @Override
    public boolean shouldDisappear(
            TownInterface town,
            Vec3 entityPosition
    ) {
        // Since cooks don't leave town. They don't need to disappear.
        return false;
    }

    @Override
    public boolean openScreen(
            ServerPlayer sp,
            VisitorMobEntity visitorMobEntity
    ) {
        // TODO: Implement Cook UI
        return false;
    }

    @Override
    public Container getInventory() {
        return inventory;
    }

    @Override
    public CookJournal.Snapshot<MCHeldItem> getJournalSnapshot() {
        return journal.getSnapshot(MCHeldItem::Air);
    }

    @Override
    public void initialize(CookJournal.Snapshot<MCHeldItem> journal) {
        this.journal.initialize(journal);
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
        return null;
    }

    @Override
    public BlockPos getTarget(
            BlockPos entityPos,
            TownInterface town
    ) {
        return null;
    }

    @Override
    public void initializeItems(Iterable<MCHeldItem> mcTownItemStream) {
        journal.setItems(mcTownItemStream);
    }

    @Override
    public boolean shouldBeNoClip(TownInterface town, BlockPos blockPos) {
        return false;
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
