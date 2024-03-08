package ca.bradj.questown.jobs.crafter;

import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.Work;
import com.google.common.collect.ImmutableMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import static ca.bradj.questown.jobs.crafter.Crafters.*;

public class CrafterPaperWork {
    public static final JobID ID = new JobID("crafter", "crafter_paper");

    public static final ImmutableMap<Integer, Ingredient> INGREDIENTS_REQUIRED_AT_STATES = ImmutableMap.of(
            BLOCK_STATE_NEED_INGREDIENTS, Ingredient.of(Items.SUGAR_CANE)
    );
    public static final ImmutableMap<Integer, Integer> INGREDIENT_QTY_REQUIRED_AT_STATES = ImmutableMap.of(
            BLOCK_STATE_NEED_INGREDIENTS, 2
    );
    public static final ImmutableMap<Integer, Ingredient> TOOLS_REQUIRED_AT_STATES = ImmutableMap.of(
    );
    public static final ImmutableMap<Integer, Integer> WORK_REQUIRED_AT_STATES = ImmutableMap.of(
            BLOCK_STATE_NEED_INGREDIENTS, 0,
            BLOCK_STATE_NEED_WORK, 10,
            BLOCK_STATE_DONE, 0
    );
    public static final ImmutableMap<Integer, Integer> TIME_REQUIRED_AT_STATES = ImmutableMap.of(
            BLOCK_STATE_NEED_INGREDIENTS, 0,
            BLOCK_STATE_NEED_WORK, 0,
            BLOCK_STATE_DONE, 0
    );
    public static final ItemStack RESULT = new ItemStack(Items.PAPER, 3);


    public static Work asWork() {
        return Crafters.asWork(
                ID,
                CrafterStickWork.ID,
                Items.PAPER.getDefaultInstance(),
                RESULT::copy,
                MAX_STATE,
                INGREDIENTS_REQUIRED_AT_STATES,
                INGREDIENT_QTY_REQUIRED_AT_STATES,
                TOOLS_REQUIRED_AT_STATES,
                WORK_REQUIRED_AT_STATES,
                TIME_REQUIRED_AT_STATES,
                100
        );
    }
}
