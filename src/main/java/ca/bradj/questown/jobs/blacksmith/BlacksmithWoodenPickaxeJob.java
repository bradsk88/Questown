package ca.bradj.questown.jobs.blacksmith;

import ca.bradj.questown.Questown;
import ca.bradj.questown.blocks.BlacksmithsTableBlock;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.*;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import static ca.bradj.questown.jobs.WorksBehaviour.productionWork;

public class BlacksmithWoodenPickaxeJob {
    public static final JobID ID = new JobID("blacksmith", "wooden_pickaxe");

    public static final int BLOCK_STATE_NEED_HANDLE = 0;
    public static final int BLOCK_STATE_NEED_HEAD = 1;
    public static final int BLOCK_STATE_NEED_WORK = 2;
    public static final int BLOCK_STATE_DONE = 3;

    public static final int MAX_STATE = BLOCK_STATE_DONE;

    public static final ImmutableMap<Integer, Ingredient> INGREDIENTS_REQUIRED_AT_STATES = ImmutableMap.of(
            BLOCK_STATE_NEED_HANDLE, Ingredient.of(Items.STICK),
            BLOCK_STATE_NEED_HEAD, Ingredient.of(ItemTags.PLANKS),
            BLOCK_STATE_NEED_WORK, Ingredient.of(ItemTags.PLANKS)
    );
    public static final ImmutableMap<Integer, Integer> INGREDIENT_QTY_REQUIRED_AT_STATES = ImmutableMap.of(
            BLOCK_STATE_NEED_HANDLE, 2,
            BLOCK_STATE_NEED_HEAD, 3,
            BLOCK_STATE_NEED_WORK, 0
    );
    public static final ImmutableMap<Integer, Ingredient> TOOLS_REQUIRED_AT_STATES = ImmutableMap.of(
    );
    public static final ImmutableMap<Integer, Integer> WORK_REQUIRED_AT_STATES = ImmutableMap.of(
            BLOCK_STATE_NEED_HANDLE, 0,
            BLOCK_STATE_NEED_HEAD, 0,
            BLOCK_STATE_NEED_WORK, 10,
            BLOCK_STATE_DONE, 0
    );
    public static final ImmutableMap<Integer, Integer> TIME_REQUIRED_AT_STATES = ImmutableMap.of(
            BLOCK_STATE_NEED_HANDLE, 0,
            BLOCK_STATE_NEED_HEAD, 0,
            BLOCK_STATE_NEED_WORK, 0,
            BLOCK_STATE_DONE, 0
    );

    public static final ItemStack RESULT = Items.WOODEN_PICKAXE.getDefaultInstance();
    public static final int PAUSE_FOR_ACTION = 100;

    public static Work asWork() {
        return productionWork(
                ID,
                new WorkDescription(
                        t -> ImmutableSet.of(MCTownItem.fromMCItemStack(RESULT)),
                        RESULT
                ),
                new WorkLocation(
                        (block) -> block instanceof BlacksmithsTableBlock,
                        Questown.ResourceLocation("smithy")
                ),
                new WorkStates(
                        MAX_STATE,
                        INGREDIENTS_REQUIRED_AT_STATES,
                        INGREDIENT_QTY_REQUIRED_AT_STATES,
                        TOOLS_REQUIRED_AT_STATES,
                        WORK_REQUIRED_AT_STATES,
                        TIME_REQUIRED_AT_STATES
                ),
                new WorkWorldInteractions(
                        PAUSE_FOR_ACTION,
                        WorksBehaviour.singleItemOutput(RESULT::copy)
                ),
                WorksBehaviour.standardProductionRules()
        );
    }
}
