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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.Collection;

public class GathererUnmappedNoToolWorkHalfDay extends NewLeaverWork {

    private static final GathererTools.LootTableParameters PARAMS = new GathererTools.LootTableParameters(
            GathererTools.NO_TOOL_TABLE_PREFIX,
            GathererTools.NO_TOOL_LOOT_TABLE_DEFAULT
    );

    static {
        allParameters.add(PARAMS);
    }

    public static final JobID ID = new JobID("gatherer", "gather_half_day");

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
    public static final ImmutableMap<Integer, String> SPECIAL_RULES = ImmutableMap.of(
            BLOCK_STATE_NEED_ROAM, SpecialRules.REMOVE_FROM_WORLD,
            ProductionStatus.FACTORY.waitingForTimedState().value(), SpecialRules.REMOVE_FROM_WORLD
    );

    public GathererUnmappedNoToolWorkHalfDay() {
        super(PARAMS);
    }

    public static Work asWork() {
        return NewLeaverWork.asWork(
                ID,
                GathererUnmappedNoToolWorkQtrDay.ID,
                Items.IRON_BOOTS.getDefaultInstance(),
                GathererTools.NO_TOOL_TABLE_PREFIX,
                Items.WHEAT_SEEDS.getDefaultInstance(),
                MAX_STATE,
                Util.constant(INGREDIENTS_REQUIRED_AT_STATES),
                Util.constant(INGREDIENT_QTY_REQUIRED_AT_STATES),
                Util.constant(TOOLS_REQUIRED_AT_STATES),
                Util.constant(WORK_REQUIRED_AT_STATES),
                ImmutableMap.of(
                        BLOCK_STATE_NEED_ROAM, () -> Config.GATHERER_TIME_REQUIRED_BASELINE.get() * 2
                ),
                SPECIAL_RULES,
                GathererUnmappedNoToolWorkHalfDay::getFromLootTables
        );
    }

    // Note: this is still declarative. In a file, we would just specify something like:
    // - Strategy: "loot_tables"
    // - Prefix: "jobs/notool"
    // - Default "jobs/notool/default"
    private static Iterable<MCHeldItem> getFromLootTables(
            ServerLevel level,
            Collection<MCHeldItem> items
    ) {
        return Loots.getFromLootTables(
                level,
                items,
                Config.GATHERER_HALF_DAY_LOOT_AMOUNT.get(),
                new GathererTools.LootTableParameters(
                        GathererTools.NO_TOOL_TABLE_PREFIX, GathererTools.NO_TOOL_LOOT_TABLE_DEFAULT
                )
        );
    }
}
