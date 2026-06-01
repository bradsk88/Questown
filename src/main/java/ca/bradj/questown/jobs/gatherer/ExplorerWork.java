package ca.bradj.questown.jobs.gatherer;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.core.init.TagsInit;
import ca.bradj.questown.core.init.items.ItemsInit;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.items.QTNBT;
import ca.bradj.questown.jobs.*;
import ca.bradj.questown.jobs.declarative.SoundInfo;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.mc.Util;
import ca.bradj.questown.town.special.SpecialQuests;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.Collection;
import java.util.List;

public class ExplorerWork {
    // The explorer is its OWN root ("explorer"), not a "gatherer" sub-job, so a villager
    // assigned to it doesn't randomly cycle into the plain gather sub-jobs during warp —
    // only "explore" is in its cycling pool, so it deterministically produces the map +
    // scouts loot every trip. It still has a gatherer parent (see asWork) so it is unlocked
    // through progression, the same way armorer/* is reached from crafter/*.
    public static final JobID ID = new JobID("explorer", "explore");

    public static final int BLOCK_STATE_NEED_FOOD = 0;
    public static final int BLOCK_STATE_NEED_PAPER = 1;
    public static final int BLOCK_STATE_USE_PAPER = 2;
    public static final int BLOCK_STATE_NEED_ROAM = 3;
    public static final int BLOCK_STATE_DONE = 4;

    public static final int MAX_STATE = BLOCK_STATE_DONE;

    public static final ImmutableMap<Integer, Ingredient> INGREDIENTS_REQUIRED_AT_STATES = ImmutableMap.of(
            BLOCK_STATE_NEED_FOOD, Ingredient.of(TagsInit.Items.VILLAGER_FOOD),
            // Paper is held as a TOOL at NEED_PAPER then CONSUMED as an ingredient here. This is
            // the cook's "tool→ingredient" idiom (cf. cook/simple_furnace_food holding then
            // consuming beef): a mid-cycle ingredient can't be acquired during warp, but a held
            // one is consumed fine — so grab it cheaply as a tool first, spend it from held here.
            BLOCK_STATE_USE_PAPER, Ingredient.of(Items.PAPER)
    );
    public static final ImmutableMap<Integer, Integer> INGREDIENT_QTY_REQUIRED_AT_STATES = ImmutableMap.of(
            BLOCK_STATE_NEED_FOOD, 1,
            BLOCK_STATE_USE_PAPER, 1
    );
    public static final ImmutableMap<Integer, Ingredient> TOOLS_REQUIRED_AT_STATES = ImmutableMap.of(
            // Held (grabbed once, carried, rendered in hand) so it survives warp supply
            // collection; spent the very next state as an ingredient (USE_PAPER).
            BLOCK_STATE_NEED_PAPER, Ingredient.of(Items.PAPER)
    );
    public static final ImmutableMap<Integer, Integer> WORK_REQUIRED_AT_STATES = ImmutableMap.of(
            // No work required
    );
    public static final ItemStack RESULT = ItemsInit.GATHERER_MAP.get().getDefaultInstance();
    private static final boolean TIMER_SHARING = false;
    public static final ImmutableMap<ProductionStatus, Collection<String>> SPECIAL_RULES = ImmutableMap.of(
            ProductionStatus.fromJobBlockStatus(BLOCK_STATE_NEED_ROAM),
            ImmutableList.of(SpecialRules.REMOVE_FROM_WORLD),
            ProductionStatus.FACTORY.waitingForTimedState(),
            ImmutableList.of(SpecialRules.REMOVE_FROM_WORLD),
            ProductionStatus.EXTRACTING_PRODUCT,
            ImmutableList.of(SpecialRules.SCOUT_LOOT)
    );


    public static final ResourceLocation JOB_SITE = SpecialQuests.TOWN_GATE;

    // Note: this is still declarative. In a file, we would just specify something like:
    // - Strategy: "loot_tables"
    // - Prefix: "jobs/axe"
    // - Default "jobs/axe/default"
    private static Iterable<MCHeldItem> getFromLootTables(
            ServerLevel level,
            Collection<MCHeldItem> items
    ) {
        ItemStack map = ItemsInit.GATHERER_MAP.get().getDefaultInstance();

        // TODO: Get from JSON files so mod can be extended with more biomes
        ImmutableList<ResourceLocation> biomes = ImmutableList.of(
                new ResourceLocation("dark_forest"),
                new ResourceLocation("desert"),
                new ResourceLocation("forest"),
                new ResourceLocation("jungle"),
                new ResourceLocation("mushroom_fields"),
                new ResourceLocation("savanna"),
                new ResourceLocation("taiga")
        );

        ResourceLocation biome = biomes.get(level.getRandom().nextInt(biomes.size()));

        QTNBT.putString(map, "biome", biome.toString());

        ImmutableList.Builder<MCHeldItem> list = ImmutableList.builder();
        list.add(MCHeldItem.fromTown(map));

        // The map is the only product. Learning a loot drop for this biome happens at
        // extraction via SpecialRules.SCOUT_LOOT, which reads this map's biome (ADR-0004).
        ImmutableList<MCHeldItem> realList = list.build();
        QT.JOB_LOGGER.debug("Presenting items to explorer: {}", realList);
        return realList;
    }

    public static Work asWork() {
        return WorksBehaviour.productionWork(
                ItemsInit.GATHERER_MAP.get().getDefaultInstance(),
                ID,
                GathererUnmappedNoToolWorkQtrDay.ID,
                WorksBehaviour.standardDescription(sl -> Ingredient.of(RESULT)),
                SpecialQuests.TOWN_GATE_LOCATION,
                new WorkStates(
                        MAX_STATE,
                        Util.constant(INGREDIENTS_REQUIRED_AT_STATES),
                        Util.constant(INGREDIENT_QTY_REQUIRED_AT_STATES),
                        Util.constant(TOOLS_REQUIRED_AT_STATES),
                        Util.constant(WORK_REQUIRED_AT_STATES),
                        ImmutableMap.of(
                                BLOCK_STATE_NEED_ROAM,
                                Config.GATHERER_TIME_REQUIRED_BASELINE::get
                        )
                ),
                new WorkWorldInteractions(
                        0, new ResultGenerator<MCHeldItem>() {
                    @Override
                    public Iterable<MCHeldItem> generate(
                            ServerLevel level,
                            Collection<MCHeldItem> heldItems
                    ) {
                        return getFromLootTables(level, heldItems);
                    }

                    @Override
                    public boolean isResultAlwaysEmpty() {
                        return false;
                    }
                }
                ),
                new WorkSpecialRules(
                        SPECIAL_RULES,
                        NewLeaverWork.standardRules()
                ),
                new SoundInfo(
                        SoundEvents.ARMOR_EQUIP_LEATHER.getLocation(),
                        100,
                        100
                )
        );
    }
}
