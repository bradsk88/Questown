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

public class GathererUnmappedAxeWorkFullDay extends NewLeaverWork {

    private static final GathererTools.LootTableParameters PARAMS = new GathererTools.LootTableParameters(
            GathererTools.AXE_LOOT_TABLE_PREFIX,
            GathererTools.AXE_LOOT_TABLE_DEFAULT
    );

    static {
        allParameters.add(PARAMS);
    }

    public static final JobID ID = new JobID("gatherer", "axe_full_day");

    public static final int BLOCK_STATE_NEED_FOOD = 0;
    public static final int BLOCK_STATE_NEED_TOOL = 1;
    public static final int BLOCK_STATE_NEED_ROAM = 2;
    public static final int BLOCK_STATE_DONE = 3;

    public static final int MAX_STATE = BLOCK_STATE_DONE;

    public static final ImmutableMap<Integer, Ingredient> INGREDIENTS_REQUIRED_AT_STATES = ImmutableMap.of(
            BLOCK_STATE_NEED_FOOD, Ingredient.of(TagsInit.Items.VILLAGER_FOOD)
    );
    public static final ImmutableMap<Integer, Integer> INGREDIENT_QTY_REQUIRED_AT_STATES = ImmutableMap.of(
            BLOCK_STATE_NEED_FOOD, 3
    );
    public static final ImmutableMap<Integer, Ingredient> TOOLS_REQUIRED_AT_STATES = ImmutableMap.of(
            BLOCK_STATE_NEED_TOOL, Ingredient.of(TagsInit.Items.AXES)
    );
    public static final ImmutableMap<Integer, Integer> WORK_REQUIRED_AT_STATES = ImmutableMap.of(
            // No work required
    );
    public static final ImmutableMap<Integer, String> SPECIAL_RULES = ImmutableMap.of(
            BLOCK_STATE_NEED_ROAM, SpecialRules.REMOVE_FROM_WORLD,
            ProductionStatus.FACTORY.waitingForTimedState().value(), SpecialRules.REMOVE_FROM_WORLD
    );

    public GathererUnmappedAxeWorkFullDay() {
        super(PARAMS);
    }

    public static Work asWork() {
        return NewLeaverWork.asWork(
                ID,
                GathererUnmappedAxeWorkHalfDay.ID, // Parent
                Items.GOLDEN_AXE.getDefaultInstance(),
                GathererTools.AXE_LOOT_TABLE_PREFIX,
                Items.OAK_WOOD.getDefaultInstance(),
                MAX_STATE,
                Util.constant(INGREDIENTS_REQUIRED_AT_STATES),
                Util.constant(INGREDIENT_QTY_REQUIRED_AT_STATES),
                Util.constant(TOOLS_REQUIRED_AT_STATES),
                Util.constant(WORK_REQUIRED_AT_STATES),
                ImmutableMap.of(
                        BLOCK_STATE_NEED_ROAM, () -> Config.GATHERER_TIME_REQUIRED_BASELINE.get() * 3
                ),
                SPECIAL_RULES,
                GathererUnmappedAxeWorkFullDay::getFromLootTables
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
        return Loots.getFromLootTables(
                level,
                items,
                Config.GATHERER_FULL_DAY_LOOT_AMOUNT.get(),
                new GathererTools.LootTableParameters(
                        GathererTools.AXE_LOOT_TABLE_PREFIX, GathererTools.AXE_LOOT_TABLE_DEFAULT
                )
        );
    }
}
