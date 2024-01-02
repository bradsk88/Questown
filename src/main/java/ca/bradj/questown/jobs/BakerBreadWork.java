package ca.bradj.questown.jobs;

import ca.bradj.questown.Questown;
import ca.bradj.questown.blocks.BreadOvenBlock;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.integration.minecraft.MCTownState;
import ca.bradj.questown.jobs.smelter.DSmelterJob;
import ca.bradj.questown.town.Warper;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.UUID;

import static ca.bradj.questown.jobs.WorksBehaviour.productionWork;

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
            BLOCK_STATE_NEED_TIME, Config.BAKING_TIME_REQUIRED_BASELINE.get(),
            BLOCK_STATE_DONE, 0
    );
    public static final Class<BreadOvenBlock> WORK_BLOCK_CLASS = BreadOvenBlock.class;
    private static final boolean TIMER_SHARING = false;

    public static final ItemStack RESULT = Items.BREAD.getDefaultInstance();
    public static final ResourceLocation WORK_ROOM_ID = new ResourceLocation(Questown.MODID, "bakery");

    public BakerBreadWork(
            UUID ownerUUID,
            int inventoryCapacity
    ) {
        super(
                ownerUUID,
                inventoryCapacity,
                ID,
                WORK_ROOM_ID,
                MAX_STATE,
                true,
                100,
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

    public static Warper<MCTownState> warper(int villagerIndex) {
        MCTownStateWorldInteraction wi = new MCTownStateWorldInteraction(
                ID, villagerIndex, 100, MAX_STATE,
                TOOLS_REQUIRED_AT_STATES,
                WORK_REQUIRED_AT_STATES,
                INGREDIENTS_REQUIRED_AT_STATES,
                INGREDIENT_QTY_REQUIRED_AT_STATES,
                TIME_REQUIRED_AT_STATES,
                () -> MCHeldItem.fromTown(RESULT)
        );
        return DeclarativeJobs.warper(wi, MAX_STATE, true);
    }

    public static Work asWork() {
        return productionWork(
                (town, uuid) -> new BakerBreadWork(uuid, 6),
                ID,
                block -> block instanceof BreadOvenBlock,
                WORK_ROOM_ID,
                t -> ImmutableSet.of(MCTownItem.fromMCItemStack(RESULT)),
                RESULT,
                wi -> warper(wi.villagerIndex()),
                INGREDIENTS_REQUIRED_AT_STATES,
                TOOLS_REQUIRED_AT_STATES
        );
    }
}
