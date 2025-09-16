package ca.bradj.questown.town.special;

import ca.bradj.questown.Questown;
import ca.bradj.questown.blocks.PlateBlock;
import ca.bradj.questown.blocks.WelcomeMatBlock;
import ca.bradj.questown.core.init.items.ItemsInit;
import ca.bradj.questown.jobs.WorkLocation;
import ca.bradj.roomrecipes.recipes.RoomRecipe;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
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
            new RoomRecipe(BROKEN, NonNullList.create(), Integer.MAX_VALUE, false),
            CAMPFIRE,
            new RoomRecipe(CAMPFIRE, NonNullList.withSize(1, Ingredient.of(Items.CAMPFIRE)), Integer.MAX_VALUE, false),
            TOWN_GATE,
            new RoomRecipe(
                    TOWN_GATE,
                    NonNullList.withSize(1, Ingredient.of(ItemsInit.WELCOME_MAT_BLOCK.get())),
                    Integer.MAX_VALUE,
                    false
            ),
            TOWN_FLAG,
            new RoomRecipe(
                    TOWN_FLAG,
                    NonNullList.withSize(1, Ingredient.of(ItemsInit.TOWN_FLAG_BLOCK.get())),
                    Integer.MAX_VALUE,
                    false
            ),
            FARM,
            new RoomRecipe(FARM, NonNullList.withSize(1, Ingredient.of(Items.DIRT)), Integer.MAX_VALUE, true)
    );
    public static final ResourceLocation BEDROOM = Questown.ResourceLocation("bedroom");
    public static final ResourceLocation JOB_BOARD = Questown.ResourceLocation("job_board");
    public static final ResourceLocation STORE_ROOM_SMALL = Questown.ResourceLocation("store_room");
    public static final ResourceLocation DINING_ROOM = Questown.ResourceLocation("dining_room");
    public static final ResourceLocation CLINIC = Questown.ResourceLocation("clinic");
    public static final WorkLocation TOWN_GATE_LOCATION = new WorkLocation(
            ctx -> SpecialQuests.isWelcomeMat(ctx.blockInfo(), ctx.blockPos()),
            SpecialQuests::isWelcomeMat,
            SpecialQuests.TOWN_GATE
    );
    public static final WorkLocation DINING_ROOM_LOCATION = new WorkLocation(
            ctx -> WorkLocation.isBlock(PlateBlock.class).test(ctx.blockInfo(), ctx.blockPos()),
            (info, pos) -> WorkLocation.isBlock(PlateBlock.class).test(info, pos),
            SpecialQuests.DINING_ROOM
    );

    public static boolean isSpecialQuest(ResourceLocation id) {
        return SPECIAL_QUESTS.containsKey(id);
    }

    public static boolean isWelcomeMat(
            WorkLocation.BlockInfo info,
            BlockPos pos
    ) {
        return WorkLocation.isBlock(WelcomeMatBlock.class).test(info, pos);
    }
}
