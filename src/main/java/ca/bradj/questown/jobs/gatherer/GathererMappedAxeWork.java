package ca.bradj.questown.jobs.gatherer;

import ca.bradj.questown.core.Config;
import ca.bradj.questown.core.init.TagsInit;
import ca.bradj.questown.core.init.items.ItemsInit;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.items.GathererMap;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.SpecialRules;
import ca.bradj.questown.jobs.Work;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.mc.Util;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class GathererMappedAxeWork extends NewLeaverWork {

    private static final GathererTools.LootTableParameters PARAMS = new GathererTools.LootTableParameters(
            GathererTools.AXE_LOOT_TABLE_PREFIX,
            GathererTools.AXE_LOOT_TABLE_DEFAULT
    );

    static {
        allParameters.add(PARAMS);
    }

    public static final JobID ID = new JobID("gatherer", "axe_mapped");

    public static final int BLOCK_STATE_NEED_FOOD = 0;
    public static final int BLOCK_STATE_NEED_MAP = 1;
    public static final int BLOCK_STATE_NEED_TOOL = 2;
    public static final int BLOCK_STATE_NEED_ROAM = 3;
    public static final int BLOCK_STATE_DONE = 4;

    public static final int MAX_STATE = BLOCK_STATE_DONE;

    public static final ImmutableMap<Integer, Ingredient> INGREDIENTS_REQUIRED_AT_STATES = ImmutableMap.of(
            BLOCK_STATE_NEED_FOOD, Ingredient.of(TagsInit.Items.VILLAGER_FOOD)
    );
    public static final ImmutableMap<Integer, Integer> INGREDIENT_QTY_REQUIRED_AT_STATES = ImmutableMap.of(
            BLOCK_STATE_NEED_FOOD, 1
    );
    public static final ImmutableMap<Integer, Ingredient> TOOLS_REQUIRED_AT_STATES = ImmutableMap.of(
            BLOCK_STATE_NEED_MAP, Ingredient.of(ItemsInit.GATHERER_MAP.get()),
            BLOCK_STATE_NEED_TOOL, Ingredient.of(TagsInit.Items.AXES)
    );
    public static final ImmutableMap<Integer, Integer> WORK_REQUIRED_AT_STATES = ImmutableMap.of(
            // No work required
    );
    public static final ImmutableMap<ProductionStatus, Collection<String>> SPECIAL_RULES = ImmutableMap.of(
            ProductionStatus.fromJobBlockStatus(BLOCK_STATE_NEED_ROAM), ImmutableList.of(SpecialRules.REMOVE_FROM_WORLD),
            ProductionStatus.FACTORY.waitingForTimedState(), ImmutableList.of(SpecialRules.REMOVE_FROM_WORLD)
    );

    public GathererMappedAxeWork(
    ) {
        super(PARAMS);
    }

    public static Work asWork() {
        return NewLeaverWork.asWork(
                ID,
                GathererUnmappedAxeWorkFullDay.ID,
                Items.DIAMOND_AXE.getDefaultInstance(),
                GathererTools.AXE_LOOT_TABLE_PREFIX,
                Items.OAK_WOOD.getDefaultInstance(),
                MAX_STATE,
                Util.constant(INGREDIENTS_REQUIRED_AT_STATES),
                Util.constant(INGREDIENT_QTY_REQUIRED_AT_STATES),
                Util.constant(TOOLS_REQUIRED_AT_STATES),
                Util.constant(WORK_REQUIRED_AT_STATES),
                ImmutableMap.of(
                        BLOCK_STATE_NEED_ROAM, Config.GATHERER_TIME_REQUIRED_BASELINE::get
                ),
                SPECIAL_RULES,
                GathererMappedAxeWork::getFromLootTables
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
        @Nullable ResourceLocation biome = GathererMap.computeBiome(items);
        if (biome == null) {
            return Loots.getFromLootTables(level, items, new GathererTools.LootTableParameters(
                    GathererTools.AXE_LOOT_TABLE_PREFIX, GathererTools.AXE_LOOT_TABLE_DEFAULT
            ));
        }
        return Loots.getFromLootTables(level, items.size(), new GathererTools.LootTableParameters(
                GathererTools.AXE_LOOT_TABLE_PREFIX, GathererTools.AXE_LOOT_TABLE_DEFAULT
        ), biome);
    }
}
