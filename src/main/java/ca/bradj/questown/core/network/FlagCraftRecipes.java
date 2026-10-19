package ca.bradj.questown.core.network;

import ca.bradj.questown.core.advancements.RoomTrigger;
import ca.bradj.questown.core.init.items.ItemsInit;
import com.google.common.collect.ImmutableList;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * The flag crafting tab's recipes, in display order: the one table both the screen (rows, buttons,
 * tooltips) and {@link FlagCraftMessage} (by index) read. Every output is stamped with the flag it
 * was crafted at (TownFlagBlock.StoreParentOnNBT), which is what lets a wand act and a mat, board,
 * mineshaft or fishing station register its room on placement. Registry-bound values are suppliers
 * so loading this class touches no registry.
 *
 * @param input         what the craft consumes (one item)
 * @param icon          the input shown on the row
 * @param output        what the player gets
 * @param descKey       the row's tooltip description
 * @param advancement   fired on craft, or null
 */
public record FlagCraftRecipes(
        Supplier<Ingredient> input,
        Supplier<ItemStack> icon,
        Supplier<Item> output,
        String descKey,
        @Nullable RoomTrigger.Triggers advancement
) {

    public static final ImmutableList<FlagCraftRecipes> ALL = ImmutableList.of(
            // The "how to register" journal entry (003) opens on getting a wand.
            new FlagCraftRecipes(() -> Ingredient.of(Items.STICK), Items.STICK::getDefaultInstance,
                    ItemsInit.TOWN_WAND::get, "menu.flag_crafting.wand_desc", RoomTrigger.Triggers.WandGet),
            // Any wooden pressure plate (the player may hold spruce, birch, ...).
            new FlagCraftRecipes(() -> Ingredient.of(ItemTags.WOODEN_PRESSURE_PLATES), Items.OAK_PRESSURE_PLATE::getDefaultInstance,
                    ItemsInit.WELCOME_MAT_BLOCK::get, "menu.flag_crafting.mat_desc", null),
            // Any sign: the #questown:job_board_inputs recipe tag is #minecraft:signs plus the board.
            new FlagCraftRecipes(() -> Ingredient.of(ItemTags.SIGNS), Items.OAK_SIGN::getDefaultInstance,
                    ItemsInit.JOB_BOARD_BLOCK::get, "menu.flag_crafting.board_desc", null),
            // Dev's recipe (2026-10-19), replacing the removed item-on-flag stamping.
            new FlagCraftRecipes(() -> Ingredient.of(Items.STONE_STAIRS), Items.STONE_STAIRS::getDefaultInstance,
                    ItemsInit.MINESHAFT_BLOCK::get, "menu.flag_crafting.mineshaft_desc", null),
            // Dev's recipe (2026-10-19): the fishing station is a stamped room block too.
            new FlagCraftRecipes(() -> Ingredient.of(Items.FISHING_ROD), Items.FISHING_ROD::getDefaultInstance,
                    ItemsInit.FISHING_STATION_BLOCK::get, "menu.flag_crafting.fishing_station_desc", null)
    );
}
