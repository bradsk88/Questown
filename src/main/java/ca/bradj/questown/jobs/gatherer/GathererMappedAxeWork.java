package ca.bradj.questown.jobs.gatherer;

import ca.bradj.questown.blocks.WelcomeMatBlock;
import ca.bradj.questown.core.init.TagsInit;
import ca.bradj.questown.core.init.items.ItemsInit;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.items.GathererMap;
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
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

import static ca.bradj.questown.jobs.JobsRegistry.getProductionNeeds;
import static ca.bradj.questown.jobs.JobsRegistry.productionJobSnapshot;

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
    public static final ImmutableMap<Integer, Integer> TIME_REQUIRED_AT_STATES = ImmutableMap.of(
            BLOCK_STATE_NEED_ROAM, 20
    );
    private static final boolean TIMER_SHARING = false;
    public static final ImmutableMap<ProductionStatus, String> SPECIAL_RULES = ImmutableMap.of(
            ProductionStatus.fromJobBlockStatus(BLOCK_STATE_NEED_ROAM), SpecialRules.REMOVE_FROM_WORLD,
            ProductionStatus.FACTORY.waitingForTimedState(), SpecialRules.REMOVE_FROM_WORLD
    );


    public static final ResourceLocation JOB_SITE = SpecialQuests.TOWN_GATE;

    public GathererMappedAxeWork(
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
                0,
                INGREDIENTS_REQUIRED_AT_STATES,
                INGREDIENT_QTY_REQUIRED_AT_STATES,
                TOOLS_REQUIRED_AT_STATES,
                WORK_REQUIRED_AT_STATES,
                TIME_REQUIRED_AT_STATES,
                TIMER_SHARING,
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
            Journal<?, MCHeldItem, ?> journal
    ) {
        @Nullable ResourceLocation biome = GathererMap.computeBiome(journal.getItems());
        if (biome == null) {
            return Loots.getFromLootTables(level, journal, new GathererTools.LootTableParameters(
                    GathererTools.AXE_LOOT_TABLE_PREFIX, GathererTools.AXE_LOOT_TABLE_DEFAULT
            ));
        }
        return Loots.getFromLootTables(level, journal, new GathererTools.LootTableParameters(
                GathererTools.AXE_LOOT_TABLE_PREFIX, GathererTools.AXE_LOOT_TABLE_DEFAULT
        ), biome);
    }

    public static JobsRegistry.Work asWork() {
        return new JobsRegistry.Work(
                (town, uuid) -> new GathererMappedAxeWork(uuid, 6),
                productionJobSnapshot(ID),
                block -> block instanceof WelcomeMatBlock,
                JOB_SITE,
                ProductionStatus.FACTORY.idle(),
                t -> t.allKnownGatherItemsFn().apply(GathererTools.AXE_LOOT_TABLE_PREFIX),
                Items.OAK_WOOD.getDefaultInstance(),
                // TODO: Needs should be biome specific
                s -> getProductionNeeds(INGREDIENTS_REQUIRED_AT_STATES, TOOLS_REQUIRED_AT_STATES)
        );
    }
}
