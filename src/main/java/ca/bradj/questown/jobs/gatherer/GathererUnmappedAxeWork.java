package ca.bradj.questown.jobs.gatherer;

import ca.bradj.questown.core.init.TagsInit;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.jobs.DeclarativeJob;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.Journal;
import ca.bradj.questown.jobs.SpecialRules;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.town.special.SpecialQuests;
import com.google.common.collect.ImmutableMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.UUID;

public class GathererUnmappedAxeWork extends DeclarativeJob {
    public static final JobID ID = new JobID("gatherer", "axe");

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
            BLOCK_STATE_NEED_ROAM, Ingredient.of(TagsInit.Items.AXES)
    );
    public static final ImmutableMap<Integer, Integer> WORK_REQUIRED_AT_STATES = ImmutableMap.of(
            BLOCK_STATE_NEED_FOOD, 0,
            BLOCK_STATE_NEED_ROAM, 0
    );
    public static final ImmutableMap<Integer, Integer> TIME_REQUIRED_AT_STATES = ImmutableMap.of(
            BLOCK_STATE_NEED_FOOD, 0,
            BLOCK_STATE_NEED_ROAM, 10 // TODO: 16000?
    );
    private static final boolean TIMER_SHARING = false;
    public static final ImmutableMap<ProductionStatus, String> SPECIAL_RULES = ImmutableMap.of(
            ProductionStatus.fromJobBlockStatus(BLOCK_STATE_NEED_ROAM), SpecialRules.REMOVE_FROM_WORLD,
            ProductionStatus.FACTORY.waitingForTimedState(), SpecialRules.REMOVE_FROM_WORLD
    );


    public static final ResourceLocation JOB_SITE = SpecialQuests.TOWN_GATE;

    public GathererUnmappedAxeWork(
            UUID ownerUUID,
            int inventoryCapacity
    ) {
        super(
                ownerUUID,
                inventoryCapacity,
                ID,
                JOB_SITE,
                MAX_STATE,
                true,
                0,
                INGREDIENTS_REQUIRED_AT_STATES,
                INGREDIENT_QTY_REQUIRED_AT_STATES,
                TOOLS_REQUIRED_AT_STATES,
                WORK_REQUIRED_AT_STATES,
                TIME_REQUIRED_AT_STATES,
                TIMER_SHARING,
                SPECIAL_RULES,
                GathererUnmappedAxeWork::getFromLootTables
        );
    }

    // Note: this is still declarative. In a file, we would just specify something like:
    // - Strategy: "loot_tables"
    // - Prefix: "jobs/axe"
    // - Default "jobs/axe/default"
    private static Iterable<MCHeldItem> getFromLootTables(
            ServerLevel level,
            Journal<?, MCHeldItem, ?> journal
    ) {
        GathererTools.LootTablePrefix ltPrefix = GathererTools.AXE_LOOT_TABLE_PREFIX;
        GathererTools.LootTablePath defaultLT = GathererTools.AXE_LOOT_TABLE_DEFAULT;
        return Loots.getFromLootTables(level, journal, ltPrefix, defaultLT);
    }
}
