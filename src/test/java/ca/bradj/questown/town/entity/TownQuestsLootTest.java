package ca.bradj.questown.town.entity;

import ca.bradj.questown.core.init.TagsInit;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.town.quests.MCQuest;
import ca.bradj.questown.town.quests.MCQuestBatch;
import ca.bradj.questown.town.quests.Quest;
import ca.bradj.questown.town.quests.QuestBatch;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TownQuestsLootTest {

    private static final QuestBatch.ChangeListener<MCQuest> NO_OP_LISTENER = new QuestBatch.ChangeListener<>() {
        @Override
        public void questCompleted(MCQuest quest) {
        }

        @Override
        public void questBatchCompleted(QuestBatch<?, ?, ?, ?> quest) {
        }

        @Override
        public void questLost(MCQuest quest) {
        }
    };

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void bindRawFoodTag() {
        // A unit test has no data packs, so the raw food tag would be empty. Bind a
        // synthetic membership so the tag match runs through the real Ingredient
        // mechanism; the actual members are data (data/questown/tags/items/
        // villager_simple_furnace_food_raw.json) and are verified by the held run.
        // Forge's bindTags rejects direct holders, so bind named holders for the items.
        List<Holder<Item>> members = Stream.of(Items.MUTTON, Items.PORKCHOP, Items.BEEF)
                .map(i -> BuiltInRegistries.ITEM.getHolderOrThrow(ResourceKey.create(Registries.ITEM, Compat.getItemId(i))))
                .collect(Collectors.toList());
        BuiltInRegistries.ITEM.bindTags(Map.of(
                TagsInit.Items.VILLAGER_SIMPLE_FURNACE_FOOD_RAW,
                members
        ));
    }

    private TownQuests townQuestsWithLootQuest() {
        return townQuestsWithLootQuest(1);
    }

    private TownQuests townQuestsWithLootQuest(int count) {
        TownQuests quests = new TownQuests();
        // Override the container listener: the real one (TownQuests) broadcasts through
        // the town, which does not exist in a unit test.
        quests.questBatches.addChangeListener(NO_OP_LISTENER);
        MCQuestBatch batch = new MCQuestBatch(null, null, new NoOpReward());
        batch.addLootQuest(null, TagsInit.Items.VILLAGER_SIMPLE_FURNACE_FOOD_RAW.location(), count);
        quests.questBatches.add(batch);
        return quests;
    }

    private boolean lootComplete(TownQuests quests) {
        return quests.questBatches.includes(q -> q.getType() == Quest.QuestType.LOOT && q.isComplete());
    }

    private boolean lootIncomplete(TownQuests quests) {
        return quests.questBatches.includes(q -> q.getType() == Quest.QuestType.LOOT && !q.isComplete());
    }

    @Test
    void processLootQuests_completesOpenLootQuest_whenTownieExtractsATagMember() {
        TownQuests quests = townQuestsWithLootQuest();
        quests.processLootQuests(MCTownItem.fromMCItemStack(new ItemStack(Items.MUTTON)));
        assertTrue(
                lootComplete(quests),
                "extracting a raw-food tag member should complete the count-1 LOOT quest"
        );
    }

    /**
     * Accumulation (2026-10-15 ruling, reversing the 2026-10-04
     * close-on-first-extraction): a count-3 LOOT quest survives two
     * extractions and completes on the third. Count-1 quests keep the old
     * close-on-first-extraction behaviour (1 >= 1).
     */
    @Test
    void processLootQuests_accumulatesAcrossExtractions_untilTheCountIsReached() {
        TownQuests quests = townQuestsWithLootQuest(3);
        quests.processLootQuests(MCTownItem.fromMCItemStack(new ItemStack(Items.MUTTON)));
        assertTrue(lootIncomplete(quests), "extraction 1 of 3 must not complete the quest");
        quests.processLootQuests(MCTownItem.fromMCItemStack(new ItemStack(Items.PORKCHOP)));
        assertTrue(lootIncomplete(quests), "extraction 2 of 3 must not complete the quest");
        quests.processLootQuests(MCTownItem.fromMCItemStack(new ItemStack(Items.BEEF)));
        assertTrue(lootComplete(quests), "the 3rd of 3 extractions completes the quest");
    }

    @Test
    void processLootQuests_creditsTheExtractionStackCount() {
        TownQuests quests = townQuestsWithLootQuest(3);
        quests.processLootQuests(MCTownItem.fromMCItemStack(new ItemStack(Items.MUTTON, 3)));
        assertTrue(lootComplete(quests), "one extraction of 3 must satisfy a count-3 quest");
    }

    @Test
    void processLootQuests_creditsEveryMatchingOpenLootQuest() {
        // Two open LOOT quests on the same tag (a re-issued ask after a batch
        // reward): one extraction must credit BOTH, not be consumed by the
        // first match (the old code returned after the first completion).
        TownQuests quests = townQuestsWithLootQuest();
        MCQuestBatch second = new MCQuestBatch(null, null, new NoOpReward());
        second.addLootQuest(null, TagsInit.Items.VILLAGER_SIMPLE_FURNACE_FOOD_RAW.location(), 1);
        quests.questBatches.add(second);
        quests.processLootQuests(MCTownItem.fromMCItemStack(new ItemStack(Items.MUTTON)));
        long complete = quests.questBatches.getAll().stream()
                .filter(q -> q.getType() == Quest.QuestType.LOOT && q.isComplete())
                .count();
        assertEquals(2, complete, "one extraction credits every matching open LOOT quest");
    }

    @Test
    void processLootQuests_doesNotComplete_whenExtractedItemIsNotATagMember() {
        TownQuests quests = townQuestsWithLootQuest();
        quests.processLootQuests(MCTownItem.fromMCItemStack(new ItemStack(Items.STONE)));
        assertTrue(
                quests.questBatches.includes(q -> q.getType() == Quest.QuestType.LOOT && !q.isComplete()),
                "a non-matching extraction must not complete the LOOT quest"
        );
    }

    @Test
    void processLootQuests_isANoop_onMissingExtraction() {
        TownQuests quests = townQuestsWithLootQuest();
        quests.processLootQuests(null);
        quests.processLootQuests(MCTownItem.fromMCItemStack(new ItemStack(Items.AIR)));
        assertTrue(
                quests.questBatches.includes(q -> q.getType() == Quest.QuestType.LOOT && !q.isComplete()),
                "a null or empty extraction must not complete the LOOT quest"
        );
    }
}
