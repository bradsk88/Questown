package ca.bradj.questown.jobs.gatherer;

import ca.bradj.questown.QT;
import ca.bradj.questown.blocks.WelcomeMatBlock;
import ca.bradj.questown.core.init.TagsInit;
import ca.bradj.questown.core.init.items.ItemsInit;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.integration.minecraft.MCTownState;
import ca.bradj.questown.items.KnowledgeMetaItem;
import ca.bradj.questown.items.QTNBT;
import ca.bradj.questown.jobs.*;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.town.Warper;
import ca.bradj.questown.town.special.SpecialQuests;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

import static ca.bradj.questown.jobs.JobsRegistry.getProductionNeeds;

public class ExplorerWork extends DeclarativeJob {
    public static final JobID ID = new JobID("gatherer", "explore");

    public static final int BLOCK_STATE_NEED_FOOD = 0;
    public static final int BLOCK_STATE_NEED_PAPER = 1;
    public static final int BLOCK_STATE_NEED_ROAM = 2;
    public static final int BLOCK_STATE_DONE = 3;

    public static final int MAX_STATE = BLOCK_STATE_DONE;

    public static final ImmutableMap<Integer, Ingredient> INGREDIENTS_REQUIRED_AT_STATES = ImmutableMap.of(
            BLOCK_STATE_NEED_FOOD, Ingredient.of(TagsInit.Items.VILLAGER_FOOD),
            BLOCK_STATE_NEED_PAPER, Ingredient.of(Items.PAPER)
    );
    public static final ImmutableMap<Integer, Integer> INGREDIENT_QTY_REQUIRED_AT_STATES = ImmutableMap.of(
            BLOCK_STATE_NEED_FOOD, 1,
            BLOCK_STATE_NEED_PAPER, 1
    );
    public static final ImmutableMap<Integer, Ingredient> TOOLS_REQUIRED_AT_STATES = ImmutableMap.of(
            // No tools required
    );
    public static final ImmutableMap<Integer, Integer> WORK_REQUIRED_AT_STATES = ImmutableMap.of(
            // No work required
    );
    public static final ImmutableMap<Integer, Integer> TIME_REQUIRED_AT_STATES = ImmutableMap.of(
            BLOCK_STATE_NEED_ROAM, 50
    );
    public static final ItemStack RESULT = ItemsInit.GATHERER_MAP.get().getDefaultInstance();
    private static final boolean TIMER_SHARING = false;
    public static final ImmutableMap<ProductionStatus, String> SPECIAL_RULES = ImmutableMap.of(
            ProductionStatus.fromJobBlockStatus(BLOCK_STATE_NEED_ROAM), SpecialRules.REMOVE_FROM_WORLD,
            ProductionStatus.FACTORY.waitingForTimedState(), SpecialRules.REMOVE_FROM_WORLD
    );


    public static final ResourceLocation JOB_SITE = SpecialQuests.TOWN_GATE;

    public ExplorerWork(
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
                ExplorerWork::getFromLootTables,
                false
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
        ItemStack map = ItemsInit.GATHERER_MAP.get().getDefaultInstance();

        // TODO[ASAP]: Get from JSON files so mod can be extended with more biomes
        ImmutableList<ResourceLocation> biomes = ImmutableList.of(
                new ResourceLocation("dark_forest"),
                new ResourceLocation("desert"),
                new ResourceLocation("jungle")
        );

        ResourceLocation biome = biomes.get(level.getRandom().nextInt(biomes.size()));

        QTNBT.putString(map, "biome", biome.toString());

        ImmutableList.Builder<MCHeldItem> list = ImmutableList.builder();
        list.add(MCHeldItem.fromTown(map));

        QT.JOB_LOGGER.debug("Presenting items to explorer: {}", list);

        List<GathererTools.LootTableParameters> all = NewLeaverWork.getAllParameters();
        if (all.isEmpty()) {
            all = ImmutableList.of(new GathererTools.LootTableParameters(
                    GathererTools.NO_TOOL_TABLE_PREFIX,
                    GathererTools.NO_TOOL_LOOT_TABLE_DEFAULT
            ));
        }

        GathererTools.LootTableParameters lootParams = all.get(level.getRandom().nextInt(all.size()));
        @NotNull List<MCHeldItem> knowledge = Loots.getFromLootTables(
                level, 1, 1,
                lootParams,
                biome
        );

        QT.JOB_LOGGER.debug(
                "Presenting knowledge of item to explorer: {} [prefix: {}, biome: {}]",
                knowledge.get(0), lootParams.prefix(), biome
        );
        list.add(KnowledgeMetaItem.wrap(knowledge.get(0), lootParams.prefix(), biome));

        return list.build();
    }

    public static JobsRegistry.Work asWork() {
        return new JobsRegistry.Work(
                (town, uuid) -> new ExplorerWork(uuid, 6),
                JobsRegistry.productionJobSnapshot(ID),
                (block) -> block instanceof WelcomeMatBlock,
                JOB_SITE,
                ProductionStatus.FACTORY.idle(),
                t -> ImmutableSet.of(MCTownItem.fromMCItemStack(RESULT)),
                RESULT,
                s -> getProductionNeeds(
                        ExplorerWork.INGREDIENTS_REQUIRED_AT_STATES,
                        ExplorerWork.TOOLS_REQUIRED_AT_STATES
                ),
                input -> warper(input)
        );
    }

    private static Warper<MCTownState> warper(JobsRegistry.WarpInput input) {
        MCTownStateWorldInteraction wi = new MCTownStateWorldInteraction(
                ID, input.villagerIndex(), 100, MAX_STATE,
                TOOLS_REQUIRED_AT_STATES,
                WORK_REQUIRED_AT_STATES,
                INGREDIENTS_REQUIRED_AT_STATES,
                INGREDIENT_QTY_REQUIRED_AT_STATES,
                TIME_REQUIRED_AT_STATES,
                () -> MCHeldItem.fromTown(RESULT)
        );
        return DeclarativeJobs.warper(wi);
    }
}
