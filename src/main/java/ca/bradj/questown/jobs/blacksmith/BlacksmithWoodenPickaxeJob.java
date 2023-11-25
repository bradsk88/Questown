package ca.bradj.questown.jobs.blacksmith;

import ca.bradj.questown.Questown;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.jobs.DeclarativeJob;
import ca.bradj.questown.jobs.JobID;
import com.google.common.collect.ImmutableMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.UUID;

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
    public static final ItemStack RESULT = Items.WOODEN_PICKAXE.getDefaultInstance();

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
                true,
                100,
                INGREDIENTS_REQUIRED_AT_STATES,
                INGREDIENT_QTY_REQUIRED_AT_STATES,
                TOOLS_REQUIRED_AT_STATES,
                WORK_REQUIRED_AT_STATES,
                RESULT::copy
        );
    }
}