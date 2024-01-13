package ca.bradj.questown.town.special;

import ca.bradj.questown.Questown;
import ca.bradj.questown.core.init.items.ItemsInit;
import ca.bradj.roomrecipes.recipes.RoomRecipe;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.Map;

public class SpecialQuests {

    public static final ResourceLocation CAMPFIRE = new ResourceLocation(Questown.MODID, "special_quest.campfire");
    public static final ResourceLocation BROKEN = new ResourceLocation(Questown.MODID, "special_quest.broken");
    public static final ResourceLocation TOWN_GATE = new ResourceLocation(Questown.MODID, "special_quest.town_gate");
    public static final ResourceLocation TOWN_FLAG = new ResourceLocation(Questown.MODID, "special_quest.town_flag");
    public static final ResourceLocation FARM = new ResourceLocation(Questown.MODID, "special_quest.farm");

    public static final Map<ResourceLocation, RoomRecipe> SPECIAL_QUESTS = ImmutableMap.of(
            BROKEN,
            new RoomRecipe(BROKEN, NonNullList.create(), Integer.MAX_VALUE),
            CAMPFIRE,
            new RoomRecipe(CAMPFIRE, NonNullList.withSize(1, Ingredient.of(Items.CAMPFIRE)), Integer.MAX_VALUE),
            TOWN_GATE,
            new RoomRecipe(TOWN_GATE, NonNullList.withSize(1, Ingredient.of(ItemsInit.WELCOME_MAT_BLOCK.get())), Integer.MAX_VALUE),
            TOWN_FLAG,
            new RoomRecipe(TOWN_FLAG, NonNullList.withSize(1, Ingredient.of(ItemsInit.TOWN_FLAG_BLOCK.get())), Integer.MAX_VALUE),
            FARM,
            new RoomRecipe(FARM, NonNullList.withSize(1, Ingredient.of(ItemsInit.TOWN_FENCE_GATE.get())), Integer.MAX_VALUE)
    );
    public static final ResourceLocation BEDROOM = new ResourceLocation(Questown.MODID, "bedroom");
    public static final ResourceLocation JOB_BOARD = new ResourceLocation(Questown.MODID, "job_board");
    public static final ResourceLocation DINING_ROOM = new ResourceLocation(Questown.MODID, "dining_room");

    public static boolean isSpecialQuest(ResourceLocation id) {
        return SPECIAL_QUESTS.containsKey(id);
    }
}
