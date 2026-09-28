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
        TownQuests quests = new TownQuests();
        // Override the container listener: the real one (TownQuests) broadcasts through
        // the town, which does not exist in a unit test.
        quests.questBatches.addChangeListener(NO_OP_LISTENER);
        MCQuestBatch batch = new MCQuestBatch(null, null, new NoOpReward());
        batch.addLootQuest(null, TagsInit.Items.VILLAGER_SIMPLE_FURNACE_FOOD_RAW.location(), 1);
        quests.questBatches.add(batch);
        return quests;
    }

    @Test
    void processLootQuests_completesOpenLootQuest_whenTownieExtractsATagMember() {
        TownQuests quests = townQuestsWithLootQuest();
        quests.processLootQuests(MCTownItem.fromMCItemStack(new ItemStack(Items.MUTTON)));
        assertTrue(
                quests.questBatches.includes(q -> q.getType() == Quest.QuestType.LOOT && q.isComplete()),
                "extracting a raw-food tag member should complete the LOOT quest"
        );
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
