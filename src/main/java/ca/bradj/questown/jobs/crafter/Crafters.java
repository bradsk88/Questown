package ca.bradj.questown.jobs.crafter;

import ca.bradj.questown.Questown;
import ca.bradj.questown.jobs.*;
import com.google.common.collect.ImmutableMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.CraftingTableBlock;

import java.util.function.Supplier;

import static ca.bradj.questown.jobs.WorksBehaviour.productionWork;

public class Crafters {

    // All crafters have the same number of states so translations get mapped correctly
    static final int BLOCK_STATE_NEED_INGREDIENTS = 0;
    static final int BLOCK_STATE_NEED_WORK = 1;
    static final int BLOCK_STATE_DONE = 2;

    static final int MAX_STATE = BLOCK_STATE_DONE;

    static Work asWork(
            JobID id,
            Supplier<ItemStack> result,
            int maxState,
            ImmutableMap<Integer, Ingredient> ingredientsRequiredAtStates,
            ImmutableMap<Integer, Integer> ingredientQtyRequiredAtStates,
            ImmutableMap<Integer, Ingredient> toolsRequiredAtStates,
            ImmutableMap<Integer, Integer> workRequiredAtStates,
            ImmutableMap<Integer, Integer> timeRequiredAtStates,
            int actionDuration
    ) {
        return productionWork(
                id,
                WorksBehaviour.standardDescription(result),
                new WorkLocation(
                        block -> block instanceof CraftingTableBlock,
                        new ResourceLocation(Questown.MODID, "crafting_room")
                ),
                new WorkStates(
                        maxState,
                        ingredientsRequiredAtStates,
                        ingredientQtyRequiredAtStates,
                        toolsRequiredAtStates,
                        workRequiredAtStates,
                        timeRequiredAtStates
                ),
                new WorkWorldInteractions(
                        actionDuration,
                        WorksBehaviour.singleItemOutput(result)
                ),
                WorksBehaviour.standardProductionRules(),
                SoundEvents.WOOD_HIT.getLocation()
        );

    }
}

