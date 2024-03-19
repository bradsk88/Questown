package ca.bradj.questown.jobs.gatherer;

import ca.bradj.questown.core.Config;
import ca.bradj.questown.core.init.TagsInit;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.SpecialRules;
import ca.bradj.questown.jobs.Work;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.mc.Util;
import com.google.common.collect.ImmutableMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.Collection;

public class GathererUnmappedRodWorkHalfDay extends NewLeaverWork {

    private static final GathererTools.LootTableParameters PARAMS = new GathererTools.LootTableParameters(
            GathererTools.FISHING_LOOT_TABLE_PREFIX,
            GathererTools.FISHING_LOOT_TABLE_DEFAULT
    );

    static {
        allParameters.add(PARAMS);
    }

    public static final JobID ID = new JobID("gatherer", "rod_half_day");

    public static final int BLOCK_STATE_NEED_FOOD = 0;
    public static final int BLOCK_STATE_NEED_TOOL = 1;
    public static final int BLOCK_STATE_NEED_ROAM = 2;
    public static final int BLOCK_STATE_DONE = 3;

    public static final int MAX_STATE = BLOCK_STATE_DONE;

    public static final ImmutableMap<Integer, Ingredient> INGREDIENTS_REQUIRED_AT_STATES = ImmutableMap.of(
            BLOCK_STATE_NEED_FOOD, Ingredient.of(TagsInit.Items.VILLAGER_FOOD)
    );
    public static final ImmutableMap<Integer, Integer> INGREDIENT_QTY_REQUIRED_AT_STATES = ImmutableMap.of(
            BLOCK_STATE_NEED_FOOD, 1
    );
    public static final ImmutableMap<Integer, Ingredient> TOOLS_REQUIRED_AT_STATES = ImmutableMap.of(
            BLOCK_STATE_NEED_TOOL, Ingredient.of(TagsInit.Items.FISHING_RODS)
    );
    public static final ImmutableMap<Integer, Integer> WORK_REQUIRED_AT_STATES = ImmutableMap.of(
            // No work required
    );
    public static final ImmutableMap<ProductionStatus, String> SPECIAL_RULES = ImmutableMap.of(
            ProductionStatus.fromJobBlockStatus(BLOCK_STATE_NEED_ROAM), SpecialRules.REMOVE_FROM_WORLD,
            ProductionStatus.FACTORY.waitingForTimedState(), SpecialRules.REMOVE_FROM_WORLD
    );

    public GathererUnmappedRodWorkHalfDay() {
        super(PARAMS);
    }

    public static Work asWork() {
        return NewLeaverWork.asWork(
                ID,
                GathererUnmappedRodWorkQtrDay.ID, // Parent
                Items.COD.getDefaultInstance(),
                GathererTools.FISHING_LOOT_TABLE_PREFIX,
                Items.COD.getDefaultInstance(),
                MAX_STATE,
                Util.constant(INGREDIENTS_REQUIRED_AT_STATES),
                Util.constant(INGREDIENT_QTY_REQUIRED_AT_STATES),
                Util.constant(TOOLS_REQUIRED_AT_STATES),
                Util.constant(WORK_REQUIRED_AT_STATES),
                ImmutableMap.of(
                        BLOCK_STATE_NEED_ROAM, () -> Config.GATHERER_TIME_REQUIRED_BASELINE.get() * 2
                ),
                SPECIAL_RULES,
                GathererUnmappedRodWorkHalfDay::getFromLootTables
        );
    }

    // Note: this is still declarative. In a file, we would just specify something like:
    // - Strategy: "loot_tables"
    // - Prefix: "jobs/axe"
    // - Default "jobs/axe/default"
    private static Iterable<MCHeldItem> getFromLootTables(
            ServerLevel level,
            Collection<MCHeldItem> items
    ) {
        int amount = Config.GATHERER_HALF_DAY_LOOT_AMOUNT.get();
        return Loots.getFromKnownTable(level, amount, amount, PARAMS, Loots.fallbackBiome, new ResourceLocation("gameplay/fishing"));
    }
}
