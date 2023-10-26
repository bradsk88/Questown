package ca.bradj.questown.jobs.smelter;

import ca.bradj.questown.Questown;
import ca.bradj.questown.core.init.TagsInit;
import ca.bradj.questown.jobs.DeclarativeJob;
import ca.bradj.questown.jobs.production.ProductionStatus;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.UUID;

public class DSmelterJob extends DeclarativeJob {
    public static final String NAME = "smelter";
    private static final int MAX_STATE = 2;
    private static final ImmutableMap<Integer, ImmutableList<Ingredient>> INGREDIENTS = ImmutableMap.of(
            0, ImmutableList.of(Ingredient.of(Items.IRON_ORE)),
            1, ImmutableList.of(),
            2, ImmutableList.of()
    );
    private static final ImmutableMap<Integer, ImmutableList<Ingredient>> TOOLS = ImmutableMap.of(
            0, ImmutableList.of(),
            1, ImmutableList.of(Ingredient.of(TagsInit.Items.PICKAXES)),
            2, ImmutableList.of()
    );
    private static final ImmutableMap<Integer, Integer> WORK = ImmutableMap.of(
            0, 0,
            1, 10,
            2, 0
    );
    private static final ImmutableBiMap<Integer, ProductionStatus> MAP = ImmutableBiMap.of(
            0, ProductionStatus.INSERTING_INGREDIENTS,
            1, ProductionStatus.WORKING_ON_PRODUCTION,
            2, ProductionStatus.EXTRACTING_PRODUCT
    );

    public DSmelterJob(
            UUID ownerUUID,
            int inventoryCapacity
    ) {
        super(
                ownerUUID,
                inventoryCapacity,
                new ResourceLocation(Questown.MODID, "smeltery"),
                MAX_STATE,
                INGREDIENTS,
                TOOLS,
                WORK,
                MAP
        );
    }
}
