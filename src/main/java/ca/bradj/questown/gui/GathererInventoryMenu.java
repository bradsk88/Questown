package ca.bradj.questown.gui;

import ca.bradj.questown.Questown;
import ca.bradj.questown.core.init.MenuTypesInit;
import ca.bradj.questown.integration.minecraft.VisitorMobEntityContainer;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import net.minecraft.client.gui.screens.inventory.HorseInventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.NonNullList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.HorseInventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;

public class GathererInventoryMenu extends AbstractContainerMenu {

    // TODO: Use slots
    public final @Nullable IItemHandler gathererInventory;
    private final IItemHandler playerInventory;

    private static final int inventoryLeftX = 8;
    private static final int boxHeight = 18, boxWidth = 18;
    private static final int margin = 4;

    public static GathererInventoryMenu ForClientSide(
            int windowId,
            Inventory inv,
            FriendlyByteBuf buf
    ) {
        int size = buf.readInt();
        VisitorMobEntity e = (VisitorMobEntity) inv.player.level.getEntity(buf.readInt());
        return new GathererInventoryMenu(windowId, new SimpleContainer(size), inv, e);
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

        layoutGathererInventorySlots(boxHeight, gathererInv.getContainerSize());
        layoutPlayerInventorySlots(86);
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
}
