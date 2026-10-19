package ca.bradj.questown.core.network;

import ca.bradj.questown.QT;
import ca.bradj.questown.blocks.TownFlagBlock;
import ca.bradj.questown.core.advancements.RoomTrigger;
import ca.bradj.questown.core.init.AdvancementsInit;
import ca.bradj.questown.mc.Compat;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
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

    /**
     * The player crafts this recipe at the flag: consume one input from their inventory, give them
     * the output stamped with the flag, and fire the recipe's advancement. The flag crafting tab
     * (FlagCraftMessage) and the village/growth driver both come through here.
     */
    public void craft(ServerPlayer player, BlockPos flagPos) {
        if (!consumeMatching(player, input().get())) {
            return;
        }
        // Every output only works against its parent flag (wand actions, mat / board / room-block
        // registration all resolve it through TownFlagBlock.GetParentFromNBT), so stamp the exact
        // flag the player crafted at.
        ItemStack crafted = new ItemStack(output().get());
        TownFlagBlock.StoreParentOnNBT(crafted, flagPos);
        giveAndNotify(player, crafted);
        if (advancement() != null) {
            AdvancementsInit.ROOM_TRIGGER.trigger(player, advancement());
        }
    }

    // The "Created X" message is the only confirmation a craft gives, so it must not be able to lie:
    // a full inventory would otherwise swallow the item while the input was still consumed. Drop the
    // remainder at the player's feet instead, the same fallback the flag uses for a block of progress.
    private static void giveAndNotify(ServerPlayer player, ItemStack crafted) {
        // The display name must be captured BEFORE add(): on success the stack is
        // consumed (count -> 0), and an empty stack's getItem() falls back to
        // Items.AIR (vanilla behavior) - that fallback is what made the message read
        // "Created Air" while the real item landed in the inventory (2026-10-11 report,
        // welcome-mat-craft-chat-says-created-air.md).
        Component itemName = crafted.getHoverName();
        if (!player.getInventory().add(crafted)) {
            player.drop(crafted, false);
        }
        player.sendSystemMessage(Compat.translatable("message.flag_crafting.created", itemName));
    }

    private static boolean consumeMatching(ServerPlayer player, Ingredient required) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (required.test(stack)) {
                stack.shrink(1);
                return true;
            }
        }
        QT.GUI_LOGGER.warn("Player {} tried to flag-craft but lacks the input item", player.getName().getString());
        return false;
    }
}
