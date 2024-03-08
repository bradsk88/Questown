package ca.bradj.questown.jobs.crafter;

import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.Work;
import com.google.common.collect.ImmutableMap;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import static ca.bradj.questown.jobs.crafter.Crafters.*;

public class CrafterPlanksWork {
    public static final JobID ID = new JobID("crafter", "crafter_planks");

    public static final ImmutableMap<Integer, Ingredient> INGREDIENTS_REQUIRED_AT_STATES = ImmutableMap.of(
            BLOCK_STATE_NEED_INGREDIENTS, Ingredient.of(ItemTags.LOGS)
    );
    public static final ImmutableMap<Integer, Integer> INGREDIENT_QTY_REQUIRED_AT_STATES = ImmutableMap.of(
            BLOCK_STATE_NEED_INGREDIENTS, 1
    );
    public static final ImmutableMap<Integer, Ingredient> TOOLS_REQUIRED_AT_STATES = ImmutableMap.of(
    );
    public static final ImmutableMap<Integer, Integer> WORK_REQUIRED_AT_STATES = ImmutableMap.of(
            BLOCK_STATE_NEED_WORK, 10
    );
    public static final ImmutableMap<Integer, Integer> TIME_REQUIRED_AT_STATES = ImmutableMap.of(
            BLOCK_STATE_NEED_WORK, 0
    );

    // TODO: Figure out how handle the "logs" tag and spit out planks of the same type as the input
    public static final ItemStack RESULT = new ItemStack(Items.OAK_PLANKS, 1);

    public static Work asWork() {
        return Crafters.asWork(
                ID,
                CrafterStickWork.ID,
                Items.OAK_PLANKS.getDefaultInstance(),
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
