package ca.bradj.questown.jobs.blacksmith;

import ca.bradj.questown.Questown;
import ca.bradj.questown.jobs.DeclarativeJob;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.UUID;

public class BlacksmithJob extends DeclarativeJob {

    public static final int BLOCK_STATE_NEED_HANDLE = 0;
    public static final int BLOCK_STATE_NEED_HEAD = 1;
    public static final int BLOCK_STATE_NEED_WORK = 2;
    public static final int BLOCK_STATE_DONE = 3;

    private static final int MAX_STATE = BLOCK_STATE_DONE;

    public static final ImmutableMap<Integer, ImmutableList<Ingredient>> INGREDIENTS_REQUIRED_AT_STATES = ImmutableMap.of(
            BLOCK_STATE_NEED_HANDLE, ImmutableList.of(
                    Ingredient.of(Items.STICK),
                    Ingredient.of(Items.STICK)
            ),
            BLOCK_STATE_NEED_HEAD, ImmutableList.of(
                    Ingredient.of(ItemTags.PLANKS),
                    Ingredient.of(ItemTags.PLANKS),
                    Ingredient.of(ItemTags.PLANKS)
            ),
            BLOCK_STATE_NEED_WORK, ImmutableList.of(),
            BLOCK_STATE_DONE, ImmutableList.of()
    );
    public static final ImmutableMap<Integer, ImmutableList<Ingredient>> TOOLS_REQUIRED_AT_STATES = ImmutableMap.of(
            BLOCK_STATE_NEED_HANDLE, ImmutableList.of(),
            BLOCK_STATE_NEED_HEAD, ImmutableList.of(),
            BLOCK_STATE_NEED_WORK, ImmutableList.of(), // Add blacksmiths hammer or something?
            BLOCK_STATE_DONE, ImmutableList.of()
    );
    public static final ImmutableMap<Integer, Integer> WORK_REQUIRED_AT_STATES = ImmutableMap.of(
            BLOCK_STATE_NEED_HANDLE, 0,
            BLOCK_STATE_NEED_HEAD, 0,
            BLOCK_STATE_NEED_WORK, 10,
            BLOCK_STATE_DONE, 0
    );

    public BlacksmithJob(
            UUID ownerUUID,
            int inventoryCapacity
    ) {
        super(
                ownerUUID,
                inventoryCapacity,
                new TranslatableComponent("jobs.blacksmith"),
                new ResourceLocation(Questown.MODID, "smithy"),
                MAX_STATE,
                INGREDIENTS_REQUIRED_AT_STATES,
                TOOLS_REQUIRED_AT_STATES,
                WORK_REQUIRED_AT_STATES
        );
    }
}
