package ca.bradj.questown.jobs;

import ca.bradj.questown.Questown;
import ca.bradj.questown.blocks.BreadOvenBlock;
import ca.bradj.questown.core.Config;
import com.google.common.collect.ImmutableMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import static ca.bradj.questown.jobs.WorksBehaviour.productionWork;

public class BakerBreadWork {
    public static final JobID ID = new JobID("baker", "bread");

    public static final int BLOCK_STATE_NEED_WHEAT = 0;
    public static final int BLOCK_STATE_NEED_COAL = 1;
    public static final int BLOCK_STATE_NEED_TIME = 2;
    public static final int BLOCK_STATE_DONE = 3;

    public static final int MAX_STATE = BLOCK_STATE_DONE;

    public static final ImmutableMap<Integer, Ingredient> INGREDIENTS_REQUIRED_AT_STATES = ImmutableMap.of(
            BLOCK_STATE_NEED_WHEAT, Ingredient.of(Items.WHEAT),
            BLOCK_STATE_NEED_COAL, Ingredient.of(ItemTags.COALS)
    );
    public static final ImmutableMap<Integer, Integer> INGREDIENT_QTY_REQUIRED_AT_STATES = ImmutableMap.of(
            BLOCK_STATE_NEED_WHEAT, 2,
            BLOCK_STATE_NEED_COAL, 1
    );
    public static final ImmutableMap<Integer, Ingredient> TOOLS_REQUIRED_AT_STATES = ImmutableMap.of(
    );
    public static final ImmutableMap<Integer, Integer> WORK_REQUIRED_AT_STATES = ImmutableMap.of(
            BLOCK_STATE_NEED_WHEAT, 0,
            BLOCK_STATE_NEED_COAL, 0,
            BLOCK_STATE_NEED_TIME, 0,
            BLOCK_STATE_DONE, 0
    );
    public static final Class<BreadOvenBlock> WORK_BLOCK_CLASS = BreadOvenBlock.class;

    public static final ItemStack RESULT = Items.BREAD.getDefaultInstance();
    public static final ResourceLocation WORK_ROOM_ID = new ResourceLocation(Questown.MODID, "bakery");
    public static final int ACTION_DURATION = 100;

    public static Work asWork() {
        return productionWork(
                ID,
                WorksBehaviour.standardDescription(() -> RESULT),
                new WorkLocation(
                        WORK_BLOCK_CLASS::isInstance,
                        WORK_ROOM_ID
                ),
                new WorkStates(
                        MAX_STATE,
                        INGREDIENTS_REQUIRED_AT_STATES,
                        INGREDIENT_QTY_REQUIRED_AT_STATES,
                        TOOLS_REQUIRED_AT_STATES,
                        WORK_REQUIRED_AT_STATES,
                        ImmutableMap.of(
                                BLOCK_STATE_NEED_WHEAT, 0,
                                BLOCK_STATE_NEED_COAL, 0,
                                BLOCK_STATE_NEED_TIME, Config.BAKING_TIME_REQUIRED_BASELINE.get(),
                                BLOCK_STATE_DONE, 0
                        )
                ),
                WorksBehaviour.standardWorldInteractions(ACTION_DURATION, () -> RESULT),
                WorksBehaviour.standardProductionRules(),
                SoundEvents.VILLAGER_WORK_BUTCHER.getLocation()
        );
    }
}
