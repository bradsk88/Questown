package ca.bradj.questown.town;

import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.quests.*;
import ca.bradj.questown.town.rewards.AddBatchOfRandomQuestsForVisitorReward;
import ca.bradj.questown.town.rewards.SpawnVisitorReward;
import ca.bradj.questown.town.special.SpecialQuests;
import ca.bradj.roomrecipes.core.Room;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Predicate;
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
        int minItems = 100 + (100 * (getVillagers(quests).size() + 1)) / 2;

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
                .filter(Objects::nonNull)
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

    public void markQuestAsComplete(MCRoom room, ResourceLocation q) {
        questBatches.markRecipeAsComplete(room, q);
    }

    public void markAsConverted(
            MCRoom oldRoom, ResourceLocation oldRecipeID,
            MCRoom newRoom, ResourceLocation newRecipeID
    ) {
        questBatches.markRecipeAsConverted(
                oldRoom, oldRecipeID,
                newRoom, newRecipeID
        );
    }

    public void markQuestAsLost(MCRoom oldRoom, ResourceLocation recipeID) {
        questBatches.markRecipeAsLost(oldRoom, recipeID);
    }

    @Override
    public void questCompleted(MCQuest quest) {
        this.changeListener.questCompleted(quest);
    }

    @Override
    public void questBatchCompleted(QuestBatch<?, ?, ?, ?> quest) {
        this.changeListener.questBatchCompleted(quest);
    }

    public void setChangeListener(TownFlagBlockEntity townFlagBlockEntity) {
        this.changeListener = townFlagBlockEntity;
    }

    public ImmutableList<Quest<ResourceLocation, MCRoom>> getAll() {
        return ImmutableList.copyOf(questBatches.getAll().stream().map(v -> (Quest<ResourceLocation, MCRoom>) v).toList());
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

    public boolean canBeUpgraded(
            ResourceLocation fromRecipeID,
            ResourceLocation toRecipeID
    ) {
        ImmutableList<Quest<ResourceLocation, MCRoom>> all = this.getAll();
        return all.stream().anyMatch(matchesToUpgrade(fromRecipeID, toRecipeID));
    }

    @NotNull
    private static Predicate<Quest<ResourceLocation, MCRoom>> matchesToUpgrade(
            ResourceLocation from, ResourceLocation to
    ) {
        return v -> v.getWantedId().equals(to) && v.fromRecipeID()
                .map(z -> z.equals(from))
                .orElse(false);
    }
}
