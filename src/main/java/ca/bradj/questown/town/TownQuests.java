package ca.bradj.questown.town;

import ca.bradj.questown.logic.RoomRecipes;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.quests.*;
import ca.bradj.questown.town.rewards.AddBatchOfRandomQuestsForVisitorReward;
import ca.bradj.questown.town.rewards.SpawnVisitorReward;
import ca.bradj.questown.town.special.SpecialQuests;
import com.google.common.collect.ImmutableList;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.Items;

import java.util.*;
import java.util.stream.Collectors;

public class TownQuests implements QuestBatch.ChangeListener<MCQuest> {
    private final Stack<PendingQuests> pendingQuests = new Stack<>();
    final MCQuestBatches questBatches = new MCQuestBatches();
    private QuestBatch.ChangeListener<MCQuest> changeListener;

    TownQuests() {
        questBatches.addChangeListener(this);
    }

    public static void setUpQuestsForNewlyPlacedFlag(TownInterface town, TownQuests quests) {
        UUID visitorUUID = UUID.randomUUID();
        MCRewardList reward = new MCRewardList(
                town,
                new SpawnVisitorReward(town, visitorUUID),
                new AddBatchOfRandomQuestsForVisitorReward(town, visitorUUID)
        );

        MCQuestBatch fireQuest = new MCQuestBatch(null, new MCDelayedReward(town, reward));
        fireQuest.addNewQuest(SpecialQuests.CAMPFIRE);

        quests.questBatches.add(fireQuest);
    }

    public static void addRandomBatchForVisitor(TownInterface town, TownQuests quests, UUID visitorUUID) {
        int minItems = 40 + (100 * getVillagers(quests).size())/2;

        UUID nextVisitorUUID = UUID.randomUUID();
        MCRewardList reward = new MCRewardList(
                town,
                new SpawnVisitorReward(town, nextVisitorUUID),
                new AddBatchOfRandomQuestsForVisitorReward(town, nextVisitorUUID)
        );
        quests.pendingQuests.push(new PendingQuests(
                minItems, visitorUUID, new MCDelayedReward(town, reward)
        ));
    }

    public static Set<UUID> getVillagers(TownQuests quests) {
        return quests.questBatches.getAllBatches()
                .stream()
                .map(MCQuestBatch::getOwner)
                .collect(Collectors.toSet());
    }

    public void tick(ServerLevel sl) {


        if (!pendingQuests.isEmpty()) {
            PendingQuests pop = pendingQuests.pop();
            Optional<MCQuestBatch> o = pop.grow(sl);
            o.ifPresentOrElse(
                    questBatches::add,
                    () -> pendingQuests.push(pop)
            );
        }

    }

    public void markQuestAsComplete(ResourceLocation q) {
        questBatches.markRecipeAsComplete(q);
    }

    @Override
    public void questCompleted(MCQuest quest) {
        this.changeListener.questCompleted(quest);
    }

    @Override
    public void questBatchCompleted(QuestBatch<?, ?, ?> quest) {
        this.changeListener.questBatchCompleted(quest);
    }

    public void setChangeListener(TownFlagBlockEntity townFlagBlockEntity) {
        this.changeListener = townFlagBlockEntity;
    }

    public ImmutableList<Quest<ResourceLocation>> getAll() {
        return ImmutableList.copyOf(questBatches.getAll().stream().map(v -> (Quest<ResourceLocation>) v).toList());
    }

    public Collection<MCQuest> getAllForVillager(UUID uuid) {
        return this.questBatches.getAllBatches()
                .stream()
                .filter(b -> uuid.equals(b.getOwner()))
                .flatMap(v -> v.getAll().stream())
                .toList();
    }

    public void addBatch(MCQuestBatch batch) {
        questBatches.add(batch);
    }

    public Collection<MCQuestBatch> getBatches() {
        return this.questBatches.getAllBatches();
    }
}
