package ca.bradj.questown.gui;

import ca.bradj.questown.mc.Compat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

public class IngredientRenderer {
    public IngredientRenderer() {
    }

    public void render(
            GuiGraphics gui,
            Ingredient i,
            int iconX,
            int yCoord
    ) {
        int curSeconds = (int) (System.currentTimeMillis() / 1000);
        ItemStack[] matchingStacks = i.getItems();
        ItemStack itemStack = matchingStacks[curSeconds % matchingStacks.length];
        Compat.renderItemAndDecorations(gui, itemStack, iconX, yCoord);
    }

    public int getSize() {
        return 16;
    }
}
