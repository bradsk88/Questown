package ca.bradj.questown.gui;

import ca.bradj.questown.core.init.MenuTypesInit;
import ca.bradj.questown.jobs.GathererJournal;
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

public class GathererInventoryMenu extends AbstractContainerMenu implements GathererJournal.StatusListener {

    public final IItemHandler gathererInventory;
    private final IItemHandler playerInventory;

    private static final int inventoryLeftX = 8;
    private static final int boxHeight = 18, boxWidth = 18;
    private static final int margin = 4;
    private final VisitorMobEntity entity;
    private final DataSlot statusSlot;

    public static GathererInventoryMenu ForClientSide(
            int windowId,
            Inventory inv,
            FriendlyByteBuf buf
    ) {
        int size = buf.readInt();
        VisitorMobEntity e = (VisitorMobEntity) inv.player.level.getEntity(buf.readInt());
        return new GathererInventoryMenu(windowId, new SimpleContainer(size) {
            @Override
            public int getMaxStackSize() {
                return 1;
            }
        }, inv, e);
    }

    public GathererInventoryMenu(
            int windowId,
            Container gathererInv,
            Inventory inv,
            VisitorMobEntity gatherer // For checking validity
    ) {
        super(MenuTypesInit.GATHERER_INVENTORY.get(), windowId);
        this.playerInventory = new InvWrapper(inv);
        this.gathererInventory = new InvWrapper(gathererInv);
        this.entity = gatherer;

        layoutPlayerInventorySlots(86); // Order is important for quickmove
        layoutGathererInventorySlots(boxHeight, gathererInv.getContainerSize());
        this.addDataSlot(this.statusSlot = DataSlot.standalone());
        this.statusSlot.set(gatherer.getStatus().ordinal());

        gatherer.setStatusListener(this);
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
    private static final int TE_INVENTORY_FIRST_SLOT_INDEX = VANILLA_FIRST_SLOT_INDEX + VANILLA_SLOT_COUNT;

    @Override
    public ItemStack quickMoveStack(Player playerIn, int index) {
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
            if (!moveItemStackTo(new ItemStack(sourceStack.getItem(), 1), lowerBound, upperBound, false)) {
                return ItemStack.EMPTY;
            }
        } else if (index < upperBound) {
            // This is a TE slot so merge the stack into the players inventory
            if (!moveItemStackTo(
                    sourceStack,
                    VANILLA_FIRST_SLOT_INDEX,
                    VANILLA_FIRST_SLOT_INDEX + VANILLA_SLOT_COUNT,
                    false
            )) {
                return ItemStack.EMPTY;
            }
        } else if (index < upperBound) {
            if (!moveItemStackTo(
                    sourceStack,
                    VANILLA_FIRST_SLOT_INDEX,
                    VANILLA_FIRST_SLOT_INDEX + VANILLA_SLOT_COUNT,
                    false
            )) {
                return ItemStack.EMPTY;
            }
        } else {
            System.out.println("Invalid slotIndex:" + index);
            return ItemStack.EMPTY;
        }
        // If stack size == 0 (the entire stack was moved) set slot contents to null
        if (sourceStack.getCount() == 0) {
            sourceSlot.set(ItemStack.EMPTY);
        } else {
            sourceSlot.setChanged();
        }
        sourceSlot.onTake(playerIn, copyOfSourceStack);
        return copyOfSourceStack;
    }

    @Override
    protected boolean moveItemStackTo(ItemStack stack, int lowerBound, int upperBound, boolean reverse) {
        boolean isGathererLB = lowerBound == TE_INVENTORY_FIRST_SLOT_INDEX;
        boolean isGathererUB = upperBound == TE_INVENTORY_FIRST_SLOT_INDEX + gathererInventory.getSlots();
        boolean isMovingToGatherer = isGathererLB && isGathererUB;

        if (!isMovingToGatherer) {
            return super.moveItemStackTo(stack, lowerBound, upperBound, reverse);
        }

        boolean flag = false;
        int i = lowerBound;

        if (!stack.isEmpty()) {
            if (reverse) {
                i = upperBound - 1;
            }

            while(true) {
                if (reverse) {
                    if (i < lowerBound) {
                        break;
                    }
                } else if (i >= upperBound) {
                    break;
                }

                Slot slot1 = this.slots.get(i);
                ItemStack itemstack1 = slot1.getItem();
                if (itemstack1.isEmpty() && slot1.mayPlace(stack)) {
                    if (stack.getCount() > slot1.getMaxStackSize()) {
                        slot1.set(stack.split(slot1.getMaxStackSize()));
                    } else {
                        slot1.set(stack.split(stack.getCount()));
                    }

                    slot1.setChanged();
                    flag = true;
                    break;
                }

                if (reverse) {
                    --i;
                } else {
                    ++i;
                }
            }
        }

        return flag;
    }

    public GathererJournal.Statuses getStatus() {
        return GathererJournal.Statuses.values()[this.statusSlot.get()];
    }

    @Override
    public void statusChanged(GathererJournal.Statuses newStatus) {
        this.statusSlot.set(newStatus.ordinal());
    }
}
