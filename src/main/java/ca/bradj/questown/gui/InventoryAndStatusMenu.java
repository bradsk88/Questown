package ca.bradj.questown.gui;

import ca.bradj.questown.core.init.MenuTypesInit;
import ca.bradj.questown.jobs.IStatus;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.StatusListener;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class InventoryAndStatusMenu extends AbstractContainerMenu implements StatusListener {

    public final IItemHandler gathererInventory;
    private final IItemHandler playerInventory;

    private static final int inventoryLeftX = 8;
    private static final int boxHeight = 18, boxWidth = 18;
    private static final int margin = 4;
    private final DataSlot statusSlot;
    final List<DataSlot> lockedSlots = new ArrayList<>(
    );
    private final JobID jobId;
    public final TownQuestsContainer questMenu;

    public static InventoryAndStatusMenu ForClientSide(
            int windowId,
            Inventory inv,
            FriendlyByteBuf buf
    ) {
        int size = buf.readInt();
        VisitorMobEntity e = (VisitorMobEntity) inv.player.level.getEntity(buf.readInt());
        JobID id = new JobID(buf.readUtf(), buf.readUtf());
        TownQuestsContainer qMenu = new TownQuestsContainer(windowId, TownQuestsContainer.readQuests(buf), TownQuestsContainer.readFlagPos(buf));
        return new InventoryAndStatusMenu(windowId,
                // Minecraft will handle filling this container by syncing from server
                new SimpleContainer(size) {
                    @Override
                    public int getMaxStackSize() {
                        return 1;
                    }
                }, inv, e.getSlotLocks(), e, qMenu, id
        );
    }


    public <S extends IStatus<S>> InventoryAndStatusMenu(
            int windowId,
            Container gathererInv,
            Inventory inv,
            Collection<Boolean> slotLocks,
            VisitorMobEntity gatherer,
            TownQuestsContainer questMenu,
            JobID jobId
// For checking validity
    ) {
        super(MenuTypesInit.GATHERER_INVENTORY.get(), windowId);
        this.questMenu = questMenu;
        this.playerInventory = new InvWrapper(inv);
        this.gathererInventory = new LockableInventoryWrapper(gathererInv, lockedSlots);
        this.jobId = jobId;

        layoutPlayerInventorySlots(86); // Order is important for quickmove
        layoutGathererInventorySlots(boxHeight, gathererInv.getContainerSize());
        this.addDataSlot(this.statusSlot = DataSlot.standalone());
        this.statusSlot.set(SessionUniqueOrdinals.getOrdinal(gatherer.getStatusForServer()));

        int i = 0;
        for (boolean locked : gatherer.getSlotLocks()) {
            DataSlot lockedSlot = this.addDataSlot(DataSlot.standalone());
            lockedSlot.set(locked ? 1 : 0);
            this.lockedSlots.add(this.addDataSlot(gatherer.getLockSlot(i)));
            i++;
        }

        gatherer.addStatusListener(this);
    }

    public boolean stillValid(Player p_38874_) {
        // TODO: Consider checking distance
        return true;
    }

    protected void layoutGathererInventorySlots(
            int pixelsFromTop,
            int numSlots
    ) {
        addLineOfBoxes(gathererInventory, 0, inventoryLeftX, pixelsFromTop, numSlots);
    }

    protected void layoutPlayerInventorySlots(
            int pixelsFromTop
    ) {
        // Player's inventory
        int rectangleRows = 3;
        addRectangleOfBoxes(playerInventory, 9, inventoryLeftX, pixelsFromTop, 9, rectangleRows);

        // Player's "hot bar" inventory
        pixelsFromTop += (boxHeight * rectangleRows) + margin;
        addLineOfBoxes(playerInventory, 0, inventoryLeftX, pixelsFromTop, 9);
    }

    protected void addRectangleOfBoxes(
            IItemHandler handler,
            int inventoryIndex,
            int leftX,
            int topY,
            int xBoxes,
            int yBoxes
    ) {
        int y = topY;
        int nextInvIndex = inventoryIndex;
        for (int j = 0; j < yBoxes; j++) {
            addLineOfBoxes(handler, nextInvIndex, leftX, y, xBoxes);
            nextInvIndex += xBoxes;
            y += boxHeight;
        }
    }

    protected void addLineOfBoxes(
            IItemHandler handler,
            int index,
            int leftX,
            int topY,
            int numBoxes
    ) {
        int x = leftX;
        int nextInvIndex = index;
        for (int i = 0; i < numBoxes; i++) {
            this.addSlot(new SlotItemHandler(handler, nextInvIndex, x, topY));
            nextInvIndex++;
            x += boxWidth;
        }
    }

    // CREDIT GOES TO: diesieben07 | https://github.com/diesieben07/SevenCommons
    // must assign a slot number to each of the slots used by the GUI.
    // For this container, we can see both the tile inventory's slots as well as the player inventory slots and the hotbar.
    // Each time we add a Slot to the container, it automatically increases the slotIndex, which means
    //  0 - 8 = hotbar slots (which will map to the InventoryPlayer slot numbers 0 - 8)
    //  9 - 35 = player inventory slots (which map to the InventoryPlayer slot numbers 9 - 35)
    //  36 - 44 = TileInventory slots, which map to our TileEntity slot numbers 0 - 8)
    private static final int HOTBAR_SLOT_COUNT = 9;
    private static final int PLAYER_INVENTORY_ROW_COUNT = 3;
    private static final int PLAYER_INVENTORY_COLUMN_COUNT = 9;
    private static final int PLAYER_INVENTORY_SLOT_COUNT = PLAYER_INVENTORY_COLUMN_COUNT * PLAYER_INVENTORY_ROW_COUNT;
    private static final int VANILLA_SLOT_COUNT = HOTBAR_SLOT_COUNT + PLAYER_INVENTORY_SLOT_COUNT;
    private static final int VANILLA_FIRST_SLOT_INDEX = 0;
    static final int TE_INVENTORY_FIRST_SLOT_INDEX = VANILLA_FIRST_SLOT_INDEX + VANILLA_SLOT_COUNT;

    @Override
    public ItemStack quickMoveStack(
            Player playerIn,
            int index
    ) {
        Slot sourceSlot = slots.get(index);
        if (sourceSlot == null || !sourceSlot.hasItem()) return ItemStack.EMPTY;  //EMPTY_ITEM
        ItemStack sourceStack = sourceSlot.getItem();
        ItemStack copyOfSourceStack = sourceStack.copy();

        // Check if the slot clicked is one of the vanilla container slots
        int upperBound = TE_INVENTORY_FIRST_SLOT_INDEX
                + gathererInventory.getSlots();
        if (index < VANILLA_FIRST_SLOT_INDEX + VANILLA_SLOT_COUNT) {
            // This is a vanilla container slot so merge the stack into the tile inventory
            int lowerBound = TE_INVENTORY_FIRST_SLOT_INDEX;
            if (!moveItemStackTo(sourceStack, lowerBound, upperBound)) {
                return ItemStack.EMPTY;
            }
        } else if (index < upperBound) {
            // This is a TE slot so merge the stack into the players inventory
            if (!moveItemStackTo(
                    sourceStack,
                    VANILLA_FIRST_SLOT_INDEX,
                    VANILLA_FIRST_SLOT_INDEX + VANILLA_SLOT_COUNT
            )) {
                return ItemStack.EMPTY;
            }
        } else {
            System.out.println("Invalid slotIndex:" + index);
            return ItemStack.EMPTY;
        }
        // If stack size == 0 (the entire stack was moved) set slot contents to empty
        if (sourceStack.getCount() == 0) {
            sourceSlot.set(ItemStack.EMPTY);
        } else {
            sourceSlot.setChanged();
        }
        sourceSlot.onTake(playerIn, copyOfSourceStack);
        return copyOfSourceStack;
    }

    @Override
    protected boolean moveItemStackTo(
            ItemStack p_38904_,
            int p_38905_,
            int p_38906_,
            boolean p_38907_
    ) {
        return moveItemStackTo(p_38904_, p_38905_, p_38906_);
    }

    protected boolean moveItemStackTo(
            ItemStack p_38904_,
            int p_38905_,
            int p_38906_
    ) {
        boolean flag = false;
        int i = p_38905_;

        ArrayList<Slot> updated = new ArrayList<>();

        if (!p_38904_.isEmpty()) {
            i = p_38905_;

            while (true) {
                if (i >= p_38906_) {
                    break;
                }

                Slot slot1 = this.slots.get(i);
                ItemStack itemstack1 = slot1.getItem();
                if (itemstack1.isEmpty() && slot1.mayPlace(p_38904_)) {
                    if (p_38904_.getCount() > slot1.getMaxStackSize()) {
                        slot1.set(p_38904_.split(slot1.getMaxStackSize()));
                    } else {
                        slot1.set(p_38904_.split(p_38904_.getCount()));
                    }
                    updated.add(slot1);
                    flag = true;

                    if (p_38904_.isEmpty()) {
                        break;
                    }
                }
                ++i;
            }
        }

        for (Slot s : updated) {
            s.setChanged();
        }

        return flag;
    }

    public IStatus<?> getStatus() {
        return SessionUniqueOrdinals.getStatus(this.statusSlot.get());
    }

    @Override
    public void statusChanged(IStatus<?> newStatus) {
        this.statusSlot.set(SessionUniqueOrdinals.getOrdinal(newStatus));
    }

    public String getRootJobId() {
        return this.jobId.rootId();
    }
}
