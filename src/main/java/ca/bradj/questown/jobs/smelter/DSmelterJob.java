package ca.bradj.questown.jobs.smelter;

import ca.bradj.questown.Questown;
import ca.bradj.questown.core.init.TagsInit;
import ca.bradj.questown.jobs.DeclarativeJob;
import ca.bradj.questown.jobs.JobID;
import com.google.common.collect.ImmutableMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.UUID;

public class DSmelterJob extends DeclarativeJob {
    public static final JobID ID = new JobID("smelter", "process_ore");
    private static final int MAX_STATE = 2;
    private static final ImmutableMap<Integer, Ingredient> INGREDIENTS = ImmutableMap.of(
            0, Ingredient.of(Items.IRON_ORE)
            // 1
            // 2
    );
    private static final ImmutableMap<Integer, Integer> INGREDIENTS_QTY = ImmutableMap.of(
            0, 1
            // 1
            // 2
    );
    private static final ImmutableMap<Integer, Ingredient> TOOLS = ImmutableMap.of(
            // 0
            1, Ingredient.of(TagsInit.Items.PICKAXES)
            // 2
    );
    private static final ImmutableMap<Integer, Integer> WORK = ImmutableMap.of(
            0, 0,
            1, 10,
            2, 0
    );

    public DSmelterJob(
            UUID ownerUUID,
            int inventoryCapacity
    ) {
        super(
                ownerUUID,
                inventoryCapacity,
                ID,
                new ResourceLocation(Questown.MODID, "smeltery"),
                MAX_STATE,
                INGREDIENTS,
                INGREDIENTS_QTY,
                TOOLS,
                WORK,
                Items.IRON_ORE::getDefaultInstance
        );
    }
}
