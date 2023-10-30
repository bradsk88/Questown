package ca.bradj.questown.jobs.blacksmith;

import ca.bradj.questown.Questown;
import ca.bradj.questown.blocks.OreProcessingBlock;
import ca.bradj.questown.jobs.DeclarativeJob;
import ca.bradj.questown.jobs.production.ProductionStatus;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.UUID;

public class BlacksmithJob extends DeclarativeJob {

    private static final int MAX_STATE = 2;
    public static final ImmutableMap<Integer, ImmutableList<Ingredient>> INGREDIENTS_REQUIRED_AT_STATES = ImmutableMap.of(
            0, ImmutableList.of(
                    Ingredient.of(Items.STICK),
                    Ingredient.of(Items.STICK),
                    Ingredient.of(ItemTags.PLANKS),
                    Ingredient.of(ItemTags.PLANKS),
                    Ingredient.of(ItemTags.PLANKS)
            ),
            1, ImmutableList.of(),
            1, ImmutableList.of()
    );
    public static final ImmutableMap<Integer, ImmutableList<Ingredient>> TOOLS_REQUIRED_AT_STATES = ImmutableMap.of(
            0, ImmutableList.of(),
            1, ImmutableList.of(),
            2, ImmutableList.of()
    );
    public static final ImmutableMap<Integer, Integer> WORK_REQUIRED_AT_STATES = ImmutableMap.of(
            0, 0,
            1, 10,
            2, 0
    );

    public BlacksmithJob(
            UUID ownerUUID,
            int inventoryCapacity
    ) {
        super(
                ownerUUID,
                inventoryCapacity,
                new ResourceLocation(Questown.MODID, OreProcessingBlock.ITEM_ID), // TODO: Change block
                MAX_STATE,
                INGREDIENTS_REQUIRED_AT_STATES,
                TOOLS_REQUIRED_AT_STATES,
                WORK_REQUIRED_AT_STATES,
                ImmutableBiMap.of(
                        0, ProductionStatus.INSERTING_INGREDIENTS,
                        1, ProductionStatus.WORKING_ON_PRODUCTION,
                        2, ProductionStatus.EXTRACTING_PRODUCT
                )
        );
    }
}
