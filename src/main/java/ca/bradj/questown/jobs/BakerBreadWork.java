package ca.bradj.questown.jobs;

import ca.bradj.questown.Questown;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.UUID;

public class BakerBreadWork extends DeclarativeJob {
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
            // TODO: Add support for work without a tool or ingredient
    );
    public static final ImmutableMap<Integer, Integer> WORK_REQUIRED_AT_STATES = ImmutableMap.of(
            BLOCK_STATE_NEED_WHEAT, 0,
            BLOCK_STATE_NEED_COAL, 0,
            BLOCK_STATE_NEED_TIME, 0,
            BLOCK_STATE_DONE, 0
    );
    public static final ImmutableMap<Integer, Integer> TIME_REQUIRED_AT_STATES = ImmutableMap.of(
            BLOCK_STATE_NEED_WHEAT, 0,
            BLOCK_STATE_NEED_COAL, 0,
            BLOCK_STATE_NEED_TIME, 100, // FIXME: 6000?
            BLOCK_STATE_DONE, 0
    );
    public static final ItemStack RESULT = Items.BREAD.getDefaultInstance();

    public BakerBreadWork(
            UUID ownerUUID,
            int inventoryCapacity
    ) {
        super(
                ownerUUID,
                inventoryCapacity,
                ID,
                new ResourceLocation(Questown.MODID, "bakery"),
                MAX_STATE,
                true,
                100,
                INGREDIENTS_REQUIRED_AT_STATES,
                INGREDIENT_QTY_REQUIRED_AT_STATES,
                TOOLS_REQUIRED_AT_STATES,
                WORK_REQUIRED_AT_STATES,
                TIME_REQUIRED_AT_STATES,
                (s, j) -> ImmutableSet.of(RESULT.copy())
        );
    }
}
