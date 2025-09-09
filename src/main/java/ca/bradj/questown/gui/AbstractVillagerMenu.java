package ca.bradj.questown.gui;

import ca.bradj.questown.QT;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.UUID;

public abstract class AbstractVillagerMenu extends AbstractContainerMenu {
    protected final BlockPos flagPos;
    protected final UUID villagerUUID;
    private final InvWrapper gathererInventory;
    protected final InvWrapper playerInventory;
    private static final int inventoryLeftX = 8;
    private static final int boxHeight = 18;
    protected int gathererInventoryYOffset = boxHeight;
    private static final int boxWidth = 18;
    private static final int margin = 4;

    protected AbstractVillagerMenu(
            @Nullable MenuType<?> p_38851_,
            @Nullable Container villagerInventory,
            @Nullable Inventory playerInventory,
            int p_38852_,
            BlockPos flagPos,
            UUID villagerUUID
    ) {
        super(p_38851_, p_38852_);
        this.flagPos = flagPos;
        this.villagerUUID = villagerUUID;
        this.gathererInventory = new InvWrapper(villagerInventory);
        this.playerInventory = new InvWrapper(playerInventory);
    }

    public BlockPos getFlagPos() {
        return flagPos;
    }

    public UUID getVillagerUUID() {
        return villagerUUID;
    }


    public boolean stillValid(Player p_38874_) {
        // TODO: Consider checking distance
        return true;
    }

    protected final void layoutSlots(Container gathererInv) {
        // It's important to call these functions in the right order.
        // Otherwise, syncing item movements to the server will not work.
        layoutPlayerInventorySlots(86);
        layoutGathererInventorySlots(gathererInventoryYOffset, gathererInv.getContainerSize());
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
        if (numBoxes <= 0) {
            QT.VILLAGER_LOGGER.error("Adding line of boxes with size zero. This is probably a bug.");
        }
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
        int upperBound = TE_INVENTORY_FIRST_SLOT_INDEX + gathererInventory.getSlots();
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

    public abstract void onClose();

    protected ImmutableList<Slot> getVillagerSlots() {
        return ImmutableList.copyOf(slots.stream().filter(v -> v.container.equals(gathererInventory)).toList());
    }

    protected @Nullable Player getPlayer() {
        if (playerInventory.getInv() instanceof Inventory i) {
            return i.player;
        }
        return null;
    }

    protected void clearContainer(Player p38940) {
        clearContainer(p38940, gathererInventory.getInv());
    }
}
