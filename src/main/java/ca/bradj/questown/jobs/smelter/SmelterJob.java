package ca.bradj.questown.jobs.smelter;

import ca.bradj.questown.Questown;
import ca.bradj.questown.blocks.OreProcessingBlock;
import ca.bradj.questown.core.init.TagsInit;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.Work;
import ca.bradj.questown.jobs.WorksBehaviour;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;

public class SmelterJob {
    public static final JobID ID = new JobID("smelter", "process_ore");
    public static final ItemStack RESULT = new ItemStack(Items.RAW_IRON, 2);
    public static final int MAX_STATE = 2;
    public static final ImmutableMap<Integer, Ingredient> INGREDIENTS = ImmutableMap.of(
            0, Ingredient.of(Items.IRON_ORE)
            // 1
            // 2
    );
    private static final ImmutableMap<Integer, Integer> INGREDIENTS_QTY = ImmutableMap.of(
            0, 1
            // 1
            // 2
    );
    public static final ImmutableMap<Integer, Ingredient> TOOLS = ImmutableMap.of(
            // 0
            1, Ingredient.of(TagsInit.Items.PICKAXES)
            // 2
    );
    private static final ImmutableMap<Integer, Integer> WORK = ImmutableMap.of(
            0, 0,
            1, 10,
            2, 0
    );
    private static final ImmutableMap<Integer, Integer> TIME = ImmutableMap.of(
            0, 0,
            1, 0,
            2, 0
    );

    public static Work asWork() {
        return WorksBehaviour.productionWork(
                ID,
                (Block block) -> block instanceof OreProcessingBlock,
                Questown.ResourceLocation("smeltery"),
                t -> ImmutableSet.of(MCTownItem.fromMCItemStack(RESULT)),
                RESULT,
                MAX_STATE,
                INGREDIENTS,
                INGREDIENTS_QTY,
                TOOLS,
                WORK,
                TIME,
                100,
                ImmutableMap.of(), // No stage rules
                WorksBehaviour.standardProductionRules(),
                WorksBehaviour.singleItemOutput(RESULT::copy)
        );
    }
}
