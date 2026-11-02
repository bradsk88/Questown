package ca.bradj.questown.core.network;

import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import ca.bradj.questown.QT;
import net.minecraft.resources.ResourceLocation;
import ca.bradj.questown.Questown;
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
 * The flag crafting tab's recipes, in display order. Each output is linked to the flag it was
 * crafted at.
 *
 * @param unlockedByQuest the room quest that shows this row once posted, or null for always
 * @param bopCost         Blocks of Progress taken from the flag instead of an item; 0 for an item recipe
 */
public record FlagCraftRecipes(
        Supplier<Ingredient> input,
        Supplier<ItemStack> icon,
        Supplier<Item> output,
        String descKey,
        @Nullable RoomTrigger.Triggers advancement,
        @Nullable ResourceLocation unlockedByQuest,
        int bopCost
) {

    public static final int TABLE_BOP_COST = 4;

    FlagCraftRecipes(
            Supplier<Ingredient> input,
            Supplier<ItemStack> icon,
            Supplier<Item> output,
            String descKey,
            @Nullable RoomTrigger.Triggers advancement,
            @Nullable ResourceLocation unlockedByQuest
    ) {
        this(input, icon, output, descKey, advancement, unlockedByQuest, 0);
    }

    private static FlagCraftRecipes forBops(Supplier<Item> output, String descKey) {
        return new FlagCraftRecipes(() -> Ingredient.EMPTY,
                () -> new ItemStack(ItemsInit.BLOCK_OF_PROGRESS.get(), TABLE_BOP_COST),
                output, descKey, null, null, TABLE_BOP_COST);
    }

    public boolean costsBops() {
        return bopCost > 0;
    }

    public static final ResourceLocation MINESHAFT_QUEST = Questown.ResourceLocation("block_room/block.questown.mineshaft");
    public static final ResourceLocation FISHING_STATION_QUEST = Questown.ResourceLocation("block_room/block.questown.fishing_station");

    public static final ImmutableList<FlagCraftRecipes> ALL = ImmutableList.of(
            new FlagCraftRecipes(() -> Ingredient.of(Items.STICK), Items.STICK::getDefaultInstance,
                    ItemsInit.TOWN_WAND::get, "menu.flag_crafting.wand_desc", RoomTrigger.Triggers.WandGet, null),
            new FlagCraftRecipes(() -> Ingredient.of(ItemTags.WOODEN_PRESSURE_PLATES), Items.OAK_PRESSURE_PLATE::getDefaultInstance,
                    ItemsInit.WELCOME_MAT_BLOCK::get, "menu.flag_crafting.mat_desc", null, null),
            new FlagCraftRecipes(() -> Ingredient.of(ItemTags.SIGNS), Items.OAK_SIGN::getDefaultInstance,
                    ItemsInit.JOB_BOARD_BLOCK::get, "menu.flag_crafting.board_desc", null, null),
            new FlagCraftRecipes(() -> Ingredient.of(Items.STONE_STAIRS), Items.STONE_STAIRS::getDefaultInstance,
                    ItemsInit.MINESHAFT_BLOCK::get, "menu.flag_crafting.mineshaft_desc", null, MINESHAFT_QUEST),
            new FlagCraftRecipes(() -> Ingredient.of(Items.FISHING_ROD), Items.FISHING_ROD::getDefaultInstance,
                    ItemsInit.FISHING_STATION_BLOCK::get, "menu.flag_crafting.fishing_station_desc", null, FISHING_STATION_QUEST),
            forBops(ItemsInit.ARMORERS_TABLE_BLOCK::get, "menu.flag_crafting.armorers_table_desc"),
            forBops(ItemsInit.BLACKSMITHS_TABLE_BLOCK::get, "menu.flag_crafting.blacksmiths_table_desc")
    );

    /** Takes one input from the player and gives them the output, linked to this flag. */
    public void craft(ServerPlayer player, BlockPos flagPos) {
        if (costsBops()) {
            if (!(player.level().getBlockEntity(flagPos) instanceof TownFlagBlockEntity flag)
                    || !flag.takeBops(bopCost())) {
                player.sendSystemMessage(Compat.translatable("message.flag_crafting.not_enough_bops", bopCost()));
                return;
            }
        } else if (!consumeMatching(player, input().get())) {
            return;
        }
        // Link the output to this flag.
        ItemStack crafted = new ItemStack(output().get());
        TownFlagBlock.StoreParentOnNBT(crafted, flagPos);
        giveAndNotify(player, crafted);
        if (advancement() != null) {
            AdvancementsInit.ROOM_TRIGGER.trigger(player, advancement());
        }
    }

    // A full inventory drops the rest at the player's feet, so the "Created X" message is true.
    private static void giveAndNotify(ServerPlayer player, ItemStack crafted) {
        // Read the name first: add() empties the stack.
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
