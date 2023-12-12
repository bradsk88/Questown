package ca.bradj.questown.jobs.crafter;

import ca.bradj.questown.Questown;
import ca.bradj.questown.jobs.DeclarativeJob;
import ca.bradj.questown.jobs.JobID;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.UUID;

public class CrafterBowlWork extends DeclarativeJob {
    public static final JobID ID = new JobID("crafter", "crafter_bowl");

    public static final int BLOCK_STATE_NEED_PLANKS = 0;
    public static final int BLOCK_STATE_NEED_WORK = 1;
    public static final int BLOCK_STATE_DONE = 2;

    public static final int MAX_STATE = BLOCK_STATE_DONE;

    public static final ImmutableMap<Integer, Ingredient> INGREDIENTS_REQUIRED_AT_STATES = ImmutableMap.of(
            BLOCK_STATE_NEED_PLANKS, Ingredient.of(ItemTags.PLANKS),
            BLOCK_STATE_NEED_WORK, Ingredient.of(ItemTags.PLANKS)
    );
    public static final ImmutableMap<Integer, Integer> INGREDIENT_QTY_REQUIRED_AT_STATES = ImmutableMap.of(
            BLOCK_STATE_NEED_PLANKS, 2,
            BLOCK_STATE_NEED_WORK, 1
    );
    public static final ImmutableMap<Integer, Ingredient> TOOLS_REQUIRED_AT_STATES = ImmutableMap.of(
            // TODO: Add support for work without a tool
    );
    public static final ImmutableMap<Integer, Integer> WORK_REQUIRED_AT_STATES = ImmutableMap.of(
            BLOCK_STATE_NEED_PLANKS, 0,
            BLOCK_STATE_NEED_WORK, 10,
            BLOCK_STATE_DONE, 0
    );
    public static final ImmutableMap<Integer, Integer> TIME_REQUIRED_AT_STATES = ImmutableMap.of(
            BLOCK_STATE_NEED_PLANKS, 0,
            BLOCK_STATE_NEED_WORK, 0,
            BLOCK_STATE_DONE, 0
    );
    public static final ItemStack RESULT = new ItemStack(Items.BOWL, 3);

    public CrafterBowlWork(
            UUID ownerUUID,
            int inventoryCapacity
    ) {
        super(
                ownerUUID,
                inventoryCapacity,
                ID,
                new ResourceLocation(Questown.MODID, "crafting_room"),
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
