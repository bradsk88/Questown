package ca.bradj.questown.town.entity;

import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.town.quests.MCQuest;
import ca.bradj.questown.town.quests.MCQuestBatch;
import ca.bradj.questown.town.quests.Quest;
import ca.bradj.questown.town.quests.QuestBatch;
import com.google.common.collect.ImmutableList;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The stock check for ITEM quests (stock in town chests) must not close a LOOT quest for the same
 * item (production only, plan ruling 16). 2026-10-26 held paths/outfitter: the hunter onboarding's
 * finished "Stone Sword" ITEM quest plus stone swords in stock closed the Outfitter's "make a Stone
 * Sword" LOOT card the moment Weapons II posted.
 */
class TownQuestsItemQuestScopeTest {

    private static final ResourceLocation STONE_SWORD = new ResourceLocation("minecraft", "stone_sword");

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final QuestBatch.ChangeListener<MCQuest> NO_OP_LISTENER = new QuestBatch.ChangeListener<>() {
        @Override
        public void questCompleted(MCQuest quest) {}

        @Override
        public void questBatchCompleted(QuestBatch<?, ?, ?, ?> quest) {}

        @Override
        public void questLost(MCQuest quest) {}
    };

    private static void addBatch(TownQuests quests, MCQuest quest) {
        MCQuestBatch.Inputs inputs = new MCQuestBatch.Inputs(quest.getBatchUUID(), null);
        inputs.addNewQuest(quest);
        quests.questBatches.add(inputs.withRewardUponCompletion(new NoOpReward()));
    }

    private static ImmutableList<MCTownItem> swords(int n) {
        return ImmutableList.of(MCTownItem.fromMCItemStack(new ItemStack(Items.STONE_SWORD, n)));
    }

    @Test
    void aFinishedItemQuest_doesNotCloseAnOpenLootQuestForTheSameItem() {
        TownQuests quests = new TownQuests();
        quests.questBatches.addChangeListener(NO_OP_LISTENER);
        UUID onboarding = UUID.randomUUID();
        addBatch(quests, MCQuest.item(onboarding, null, STONE_SWORD, 1));
        quests.processItemQuests(swords(1));
        assertTrue(quests.questBatches.getAll().stream().allMatch(Quest::isComplete), "precondition: the ITEM quest closes on stock");

        addBatch(quests, MCQuest.loot(UUID.randomUUID(), null, STONE_SWORD, 1));
        quests.processItemQuests(swords(4));

        assertFalse(quests.questBatches.getAll().stream()
                .filter(q -> q.getType() == Quest.QuestType.LOOT)
                .anyMatch(Quest::isComplete), "a LOOT quest counts production, not stock");
    }

    @Test
    void anOpenItemQuest_closesItself_notALootQuestPostedEarlier() {
        TownQuests quests = new TownQuests();
        quests.questBatches.addChangeListener(NO_OP_LISTENER);
        addBatch(quests, MCQuest.loot(UUID.randomUUID(), null, STONE_SWORD, 1));
        addBatch(quests, MCQuest.item(UUID.randomUUID(), null, STONE_SWORD, 1));

        quests.processItemQuests(swords(1));

        for (MCQuest q : quests.questBatches.getAll()) {
            assertTrue(q.getType() == Quest.QuestType.ITEM ? q.isComplete() : !q.isComplete(), q.getType().toString());
        }
    }
}
