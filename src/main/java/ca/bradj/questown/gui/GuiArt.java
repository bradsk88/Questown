package ca.bradj.questown.gui;

import ca.bradj.questown.Questown;
import ca.bradj.questown.mc.IconButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.resources.ResourceLocation;

/**
 * The panel, tab, slot and button art the screens share. The textures are JEI's (MIT), shipped with Questown
 * so the screens don't depend on JEI's internals, which change between JEI versions.
 */
public class GuiArt {
    private static final GuiSprite TAB_SELECTED = sprite("tab_selected", 24, 24);
    private static final GuiSprite TAB_UNSELECTED = sprite("tab_unselected", 24, 24);
    private static final GuiSprite SLOT = sprite("slot", 18, 18);
    private static final GuiSprite ARROW_NEXT = new GuiSprite(texture("icons/arrow_next"), 9, 9, 0, 0, 1, 1);
    private static final GuiSprite ARROW_PREVIOUS = new GuiSprite(texture("icons/arrow_previous"), 9, 9, 0, 0, 1, 1);
    private static final GuiSprite PLUS = sprite("icons/recipe_transfer", 7, 7);
    private static final NineSlice GUI_BACKGROUND = nineSlice("gui_background", 64, 16);
    private static final NineSlice RECIPE_BACKGROUND = nineSlice("single_recipe_background", 64, 16);
    private static final NineSlice BUTTON_ENABLED = nineSlice("button_enabled", 20, 6);
    private static final NineSlice BUTTON_DISABLED = nineSlice("button_disabled", 20, 6);
    private static final NineSlice BUTTON_HIGHLIGHT = nineSlice("button_highlight", 20, 6);

    public static GuiSprite getTabSelected() {
        return TAB_SELECTED;
    }

    public static GuiSprite getTabUnselected() {
        return TAB_UNSELECTED;
    }

    public static NineSlice getRecipeGuiBackground() {
        return GUI_BACKGROUND;
    }

    public static NineSlice getRecipeBackground() {
        return RECIPE_BACKGROUND;
    }

    public static GuiSprite getArrowNext() {
        return ARROW_NEXT;
    }

    public static GuiSprite getArrowPrevious() {
        return ARROW_PREVIOUS;
    }

    public static GuiSprite getPlusIcon() {
        return PLUS;
    }

    public static GuiSprite getSlotDrawable() {
        return SLOT;
    }

    public static NineSlice button(
            boolean enabled,
            boolean hovered
    ) {
        if (!enabled) {
            return BUTTON_DISABLED;
        }
        return hovered ? BUTTON_HIGHLIGHT : BUTTON_ENABLED;
    }

    public static IconButton iconButton(
            int x,
            int y,
            int width,
            int height,
            GuiSprite icon,
            Button.OnPress onPress
    ) {
        return new IconButton(x, y, width, height, icon, onPress);
    }

    private static ResourceLocation texture(String name) {
        return Questown.ResourceLocation("textures/gui/jei/%s.png", name);
    }

    private static GuiSprite sprite(
            String name,
            int width,
            int height
    ) {
        return new GuiSprite(texture(name), width, height);
    }

    private static NineSlice nineSlice(
            String name,
            int size,
            int border
    ) {
        return new NineSlice(texture(name), size, size, border, border, border, border);
    }
}
