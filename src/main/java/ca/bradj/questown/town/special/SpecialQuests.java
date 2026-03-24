package ca.bradj.questown.town.special;

import ca.bradj.questown.blocks.PlateBlock;
import ca.bradj.questown.blocks.TownFlagBlock;
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

    private static final String MOD = "questown";

    public static final ResourceLocation CAMPFIRE = new ResourceLocation(MOD, "special_quest.campfire");
    public static final ResourceLocation BROKEN = new ResourceLocation(MOD, "special_quest.broken");
    public static final ResourceLocation TOWN_GATE = new ResourceLocation(MOD, "special_quest.town_gate");
    public static final ResourceLocation TOWN_FLAG = new ResourceLocation(MOD, "special_quest.town_flag");
    public static final ResourceLocation FARM = new ResourceLocation(MOD, "special_quest.farm");
    public static final ResourceLocation BEDROOM = new ResourceLocation(MOD, "bedroom");
    public static final ResourceLocation JOB_BOARD = new ResourceLocation(MOD, "job_board");
    public static final ResourceLocation STORE_ROOM_SMALL = new ResourceLocation(MOD, "store_room");
    public static final ResourceLocation DINING_ROOM = new ResourceLocation(MOD, "dining_room");
    public static final ResourceLocation CLINIC = new ResourceLocation(MOD, "clinic");

    private static volatile Map<ResourceLocation, RoomRecipe> specialQuestsCache;

    public static Map<ResourceLocation, RoomRecipe> getSpecialQuests() {
        if (specialQuestsCache == null) {
            specialQuestsCache = ImmutableMap.of(
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
        }
        return specialQuestsCache;
    }

    @Deprecated
    public static final Map<ResourceLocation, RoomRecipe> SPECIAL_QUESTS = new java.util.AbstractMap<>() {
        @Override
        public java.util.Set<Entry<ResourceLocation, RoomRecipe>> entrySet() {
            return getSpecialQuests().entrySet();
        }

        @Override
        public RoomRecipe get(Object key) {
            return getSpecialQuests().get(key);
        }

        @Override
        public boolean containsKey(Object key) {
            return getSpecialQuests().containsKey(key);
        }

        @Override
        public int size() {
            return getSpecialQuests().size();
        }
    };
    public static final WorkLocation TOWN_GATE_LOCATION = new WorkLocation(
            ctx -> SpecialQuests.isWelcomeMat(ctx.blockInfo(), ctx.blockPos()),
            SpecialQuests::isWelcomeMat,
            SpecialQuests.TOWN_GATE
    );
    public static final WorkLocation TOWN_FLAG_LOCATION = new WorkLocation(
            ctx -> SpecialQuests.isFlag(ctx.blockInfo(), ctx.blockPos()),
            SpecialQuests::isFlag,
            SpecialQuests.TOWN_FLAG
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
    public static boolean isFlag(
            WorkLocation.BlockInfo info,
            BlockPos pos
    ) {
        return WorkLocation.isBlock(TownFlagBlock.class).test(info, pos);
    }
}
