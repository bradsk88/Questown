package ca.bradj.questown.jobs.blacksmith;

import ca.bradj.questown.Questown;
import ca.bradj.questown.blocks.BlacksmithsTableBlock;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.integration.minecraft.MCTownState;
import ca.bradj.questown.jobs.*;
import ca.bradj.questown.town.Warper;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.UUID;
import java.util.function.Supplier;

import static ca.bradj.questown.jobs.WorksBehaviour.productionJobSnapshot;
import static ca.bradj.questown.jobs.WorksBehaviour.productionWork;

public class BlacksmithWoodenPickaxeJob extends DeclarativeJob {
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
            BLOCK_STATE_NEED_HEAD, 2,
            BLOCK_STATE_NEED_WORK, 1
    );
    public static final ImmutableMap<Integer, Ingredient> TOOLS_REQUIRED_AT_STATES = ImmutableMap.of(
            // TODO: Add support for work without a tool
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
    private static final boolean TIMER_SHARING = false;

    public static final ItemStack RESULT = Items.WOODEN_PICKAXE.getDefaultInstance();
    public static final boolean PRIORITIZE_EXTRACTION = true;
    public static final int PAUSE_FOR_ACTION = 100;

    public BlacksmithWoodenPickaxeJob(
            UUID ownerUUID,
            int inventoryCapacity
    ) {
        super(
                ownerUUID,
                inventoryCapacity,
                ID,
                new ResourceLocation(Questown.MODID, "smithy"),
                MAX_STATE,
                PRIORITIZE_EXTRACTION,
                PAUSE_FOR_ACTION,
                INGREDIENTS_REQUIRED_AT_STATES,
                INGREDIENT_QTY_REQUIRED_AT_STATES,
                TOOLS_REQUIRED_AT_STATES,
                WORK_REQUIRED_AT_STATES,
                TIME_REQUIRED_AT_STATES,
                TIMER_SHARING,
                ImmutableMap.of(),
                (s, j) -> ImmutableSet.of(MCHeldItem.fromMCItemStack(RESULT.copy())),
                false
        );
    }

    public static Work asWork() {
        return productionWork(
                (town, uuid) -> new BlacksmithWoodenPickaxeJob(uuid, 6),
                // TODO: Add support for smaller inventories
                BlacksmithWoodenPickaxeJob.ID,
                (block) -> block instanceof BlacksmithsTableBlock,
                Questown.ResourceLocation("smithy"),
                t -> ImmutableSet.of(MCTownItem.fromMCItemStack(BlacksmithWoodenPickaxeJob.RESULT)),
                BlacksmithWoodenPickaxeJob.RESULT,
                BlacksmithWoodenPickaxeJob.INGREDIENTS_REQUIRED_AT_STATES,
                BlacksmithWoodenPickaxeJob.TOOLS_REQUIRED_AT_STATES
        );
    }
}
