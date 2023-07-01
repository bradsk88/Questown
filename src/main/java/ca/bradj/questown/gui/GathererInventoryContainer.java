package ca.bradj.questown.gui;

import ca.bradj.questown.core.init.MenuTypesInit;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import com.google.common.collect.ImmutableList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;

import java.util.ArrayList;

public class GathererInventoryContainer extends AbstractContainerMenu {

    // TODO: Use slots
    public final ImmutableList<ResourceLocation> gathererInventory;
    private final IItemHandler playerInventory;

    private static final int inventoryLeftX = 8;
    private static final int boxHeight = 18, boxWidth = 18;
    private static final int margin = 4;

    public GathererInventoryContainer(int windowId, Inventory inv, FriendlyByteBuf buf) {
        super(MenuTypesInit.GATHERER_INVENTORY.get(), windowId);
        this.playerInventory = new InvWrapper(inv);

        layoutPlayerInventorySlots(86);

        int capacity = buf.readInt();
        ArrayList<ResourceLocation> r = buf.readCollection(c -> new ArrayList<>(capacity), c -> {
            ResourceLocation itemID = c.readResourceLocation();
            return itemID;
        });
        this.gathererInventory = ImmutableList.copyOf(r);
    }

    public GathererInventoryContainer(
            int windowId,
            Inventory inventory,
            ImmutableList<MCTownItem> items
    ) {
        super(MenuTypesInit.GATHERER_INVENTORY.get(), windowId);
        this.playerInventory = new InvWrapper(inventory);

        ImmutableList.Builder<ResourceLocation> b = ImmutableList.builder();
        items.forEach(v -> v.get().getRegistryName());
        this.gathererInventory = b.build();
    }

    public boolean stillValid(Player p_38874_) {
        // TODO: Consider checking distance
        return true;
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

    protected void addRectangleOfBoxes(IItemHandler handler, int inventoryIndex, int leftX, int topY, int xBoxes, int yBoxes) {
        int y = topY;
        int nextInvIndex = inventoryIndex;
        for (int j = 0; j < yBoxes; j++) {
            addLineOfBoxes(handler, nextInvIndex, leftX, y, xBoxes);
            nextInvIndex += xBoxes;
            y += boxHeight;
        }
    }

    protected void addLineOfBoxes(IItemHandler handler, int index, int leftX, int topY, int numBoxes) {
        int x =  leftX;
        int nextInvIndex = index;
        for (int i = 0; i < numBoxes; i++) {
            this.addSlot(new SlotItemHandler(handler, nextInvIndex, x, topY));
            nextInvIndex++;
            x += boxWidth;
        }
    }
}
