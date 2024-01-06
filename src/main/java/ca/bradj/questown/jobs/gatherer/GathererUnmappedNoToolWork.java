package ca.bradj.questown.jobs.gatherer;

import ca.bradj.questown.blocks.WelcomeMatBlock;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.core.init.TagsInit;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.JobsRegistry;
import ca.bradj.questown.jobs.Journal;
import ca.bradj.questown.jobs.SpecialRules;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.town.special.SpecialQuests;
import com.google.common.collect.ImmutableMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.UUID;

import static ca.bradj.questown.jobs.JobsRegistry.getProductionNeeds;
import static ca.bradj.questown.jobs.JobsRegistry.productionJobSnapshot;

public class GathererUnmappedNoToolWork extends NewLeaverWork {

    private static final GathererTools.LootTableParameters PARAMS = new GathererTools.LootTableParameters(
            GathererTools.NO_TOOL_TABLE_PREFIX,
            GathererTools.NO_TOOL_LOOT_TABLE_DEFAULT
    );

    static {
        allParameters.add(PARAMS);
    }

    public static final JobID ID = new JobID("gatherer", "notool");

    public static final int BLOCK_STATE_NEED_FOOD = 0;
    public static final int BLOCK_STATE_NEED_ROAM = 1;
    public static final int BLOCK_STATE_DONE = 2;

    public static final int MAX_STATE = BLOCK_STATE_DONE;

    public static final ImmutableMap<Integer, Ingredient> INGREDIENTS_REQUIRED_AT_STATES = ImmutableMap.of(
            BLOCK_STATE_NEED_FOOD, Ingredient.of(TagsInit.Items.VILLAGER_FOOD)
    );
    public static final ImmutableMap<Integer, Integer> INGREDIENT_QTY_REQUIRED_AT_STATES = ImmutableMap.of(
            BLOCK_STATE_NEED_FOOD, 1
    );
    public static final ImmutableMap<Integer, Ingredient> TOOLS_REQUIRED_AT_STATES = ImmutableMap.of(
    );
    public static final ImmutableMap<Integer, Integer> WORK_REQUIRED_AT_STATES = ImmutableMap.of(
            // No work required
    );
    public static final ImmutableMap<Integer, Integer> TIME_REQUIRED_AT_STATES = ImmutableMap.of(
            BLOCK_STATE_NEED_ROAM, Config.GATHERER_TIME_REQUIRED_BASELINE.get()
    );
    private static final boolean TIMER_SHARING = false;
    public static final ImmutableMap<ProductionStatus, String> SPECIAL_RULES = ImmutableMap.of(
            ProductionStatus.fromJobBlockStatus(BLOCK_STATE_NEED_ROAM), SpecialRules.REMOVE_FROM_WORLD,
            ProductionStatus.FACTORY.waitingForTimedState(), SpecialRules.REMOVE_FROM_WORLD
    );

    public static final ResourceLocation JOB_SITE = SpecialQuests.TOWN_GATE;

    public GathererUnmappedNoToolWork(
            UUID ownerUUID,
            int inventoryCapacity
    ) {
        super(
                ownerUUID,
                PARAMS,
                inventoryCapacity,
                ID,
                JOB_SITE,
                MAX_STATE,
                true,
                1,
                INGREDIENTS_REQUIRED_AT_STATES,
                INGREDIENT_QTY_REQUIRED_AT_STATES,
                TOOLS_REQUIRED_AT_STATES,
                WORK_REQUIRED_AT_STATES,
                TIME_REQUIRED_AT_STATES,
                TIMER_SHARING,
                SPECIAL_RULES,
                GathererUnmappedNoToolWork::getFromLootTables
        );
    }

    // Note: this is still declarative. In a file, we would just specify something like:
    // - Strategy: "loot_tables"
    // - Prefix: "jobs/notool"
    // - Default "jobs/notool/default"
    private static Iterable<MCHeldItem> getFromLootTables(
            ServerLevel level,
            Journal<?, MCHeldItem, ?> journal
    ) {
        return Loots.getFromLootTables(level, journal, new GathererTools.LootTableParameters(
                GathererTools.NO_TOOL_TABLE_PREFIX, GathererTools.NO_TOOL_LOOT_TABLE_DEFAULT
        ));
    }

    public static JobsRegistry.Work asWork() {

        return new JobsRegistry.Work(
                (town, uuid) -> new GathererUnmappedNoToolWork(uuid, 6),
                productionJobSnapshot(ID),
                block -> block instanceof WelcomeMatBlock,
                JOB_SITE,
                ProductionStatus.FACTORY.idle(),
                t -> t.allKnownGatherItemsFn().apply(GathererTools.NO_TOOL_TABLE_PREFIX),
                Items.WHEAT_SEEDS.getDefaultInstance(),
                s -> getProductionNeeds(INGREDIENTS_REQUIRED_AT_STATES, TOOLS_REQUIRED_AT_STATES)
        );
    }
}
