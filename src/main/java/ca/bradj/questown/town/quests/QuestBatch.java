package ca.bradj.questown.town.quests;

import ca.bradj.questown.QT;
import ca.bradj.roomrecipes.core.Room;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

// Quests is a unit testable module for the quests of a town
public class QuestBatch<
        KEY,
        ROOM extends Room,
        QUEST extends Quest<KEY, ROOM>,
        REWARD extends Reward
        > {

    private final List<QUEST> quests = new ArrayList<>();
    @NotNull protected REWARD reward;

    private final Quest.QuestFactory<KEY, ROOM, QUEST> questFactory;

    // TODO: Support multiple?
    private ChangeListener<QUEST> changeListener = new ChangeListener<>() {
        @Override
        public void questCompleted(QUEST quest) {
            // No op by default
        }

        @Override
        public void questBatchCompleted(
                QuestBatch<?, ?, ?, ?> quest
        ) {
            // No op by default
        }

        @Override
        public void questLost(QUEST foundQuest) {
            // No op by default
        }
    };
    private UUID batchUUID;

    QuestBatch(
            Quest.QuestFactory<KEY, ROOM, QUEST> qf,
            @NotNull REWARD reward,
            @NotNull UUID batchUUID
    ) {
        this.questFactory = qf;
        this.reward = reward;
        this.batchUUID = batchUUID;
    }

    public void addChangeListener(ChangeListener<QUEST> changeListener) {
        this.changeListener = changeListener;
    }

    public Collection<KEY> getCompletedRecipeIDs() {
        return this.quests.stream().filter(Quest::isComplete).map(Quest::getWantedId).toList();
    }

    public void addNewQuest(@Nullable UUID ownerId, KEY id) {
        this.quests.add(this.questFactory.newQuest(ownerId, id));
    }

    public void addNewUpgradeQuest(@Nullable UUID ownerId, KEY fromID, KEY toID) {
        this.quests.add(this.questFactory.newUpgradeQuest(ownerId, fromID, toID));
    }

    public ImmutableList<QUEST> getAll() {
        return ImmutableList.copyOf(this.quests);
    }

    void initialize(
            ImmutableList<QUEST> aqs,
            REWARD reward
    ) {
        if (this.quests.size() > 0) {
            throw new IllegalStateException("Quests already initialized");
        }
        this.quests.addAll(aqs);
        this.reward = reward;
    }

    public boolean markRecipeAsComplete(ROOM room, KEY recipe) {
        Stream<QUEST> matches = this.quests.stream().filter(v -> recipe.equals(v.getWantedId()));
        Optional<QUEST> incomplete = matches.filter(v -> !v.isComplete()).findFirst();
        if (incomplete.isEmpty()) {
            return false;
        }
        QUEST quest = incomplete.get();
        this.quests.remove(quest);
        QUEST updated = this.questFactory.completed(room, quest);
        this.quests.add(updated);
        this.changeListener.questCompleted(updated);

        incomplete = this.quests.stream().filter(v -> !v.isComplete()).findFirst();
        if (incomplete.isEmpty()) {
            this.reward.claim();
            this.changeListener.questBatchCompleted(this);
        }
        return true;
    }

    public boolean canMarkRecipeAsConverted(KEY oldRecipeID, KEY newRecipeID) {
        Stream<QUEST> newMatches = this.quests.stream()
                .filter(v -> newRecipeID.equals(v.getWantedId()));
        Optional<QUEST> incomplete = newMatches.filter(Predicate.not(Quest::isComplete)).findFirst();
        return incomplete.isPresent() &&
                incomplete.get().fromRecipeID()
                        .map(v -> v.equals(oldRecipeID))
                        .orElse(false);
    }

    public boolean markRecipeAsConverted(
            ROOM newRoom,
            KEY oldRecipeID,
            KEY newRecipeID
    ) {
        Stream<QUEST> newMatches = this.quests.stream()
                .filter(v -> newRecipeID.equals(v.getWantedId()));
        Optional<QUEST> incomplete = newMatches
                .filter(Predicate.not(Quest::isComplete))
                .findFirst();
        if (incomplete.isEmpty()) {
            return false;
        }
        QUEST converted = incomplete.get();
        Optional<KEY> fr = converted.fromRecipeID();
        if (fr.isEmpty() || !fr.get().equals(oldRecipeID)) {
            return false;
        }
        this.quests.remove(converted);
        QUEST convCompleted = this.questFactory.completed(newRoom, converted);
        this.quests.add(convCompleted);
        this.changeListener.questCompleted(convCompleted);

        incomplete = this.quests.stream().filter(v -> !v.isComplete()).findFirst();
        if (incomplete.isEmpty()) {
            this.reward.claim();
            this.changeListener.questBatchCompleted(this);
        }
        return true;
    }

    public static <R extends Room, Q extends Quest<?, R>> Stream<Q> stream(QuestBatch<?, R, Q, ?> batch) {
        return batch.quests.stream();
    }

    public REWARD getReward() {
        return reward;
    }

    public int size() {
        return this.quests.size();
    }

    public void changeRoomOnly(ROOM oldRoom, ROOM newRoom) {
        List<QUEST> snapshot = ImmutableList.copyOf(this.quests);
        this.quests.clear();
        this.quests.addAll(snapshot.stream().peek(
                v -> {
                    if (oldRoom.equals(v.completedOn)) {
                        QT.QUESTS_LOGGER.debug(
                                "Quest completion room updated after room size change. {} -> {}",
                                oldRoom, newRoom
                        );
                        v.completedOn = newRoom;
                    }
                }
        ).toList());
    }

    public @Nullable QUEST findMatch(ROOM room, KEY oldRecipeID) {
        Optional<QUEST> found = this.quests.stream()
                .filter(v -> room.equals(v.completedOn) && oldRecipeID.equals(v.recipeId))
                .findFirst();
        return found.orElse(null);
    }

    public void markConsumed(QUEST match) {
        // TODO: Instead of removing quests, change their status so it gets filtered out
        //  of UIs by default, but can still be audited.
        this.quests.remove(match);
    }

    public boolean markRecipeAsLost(@NotNull ROOM oldRoom, @NotNull KEY recipeID) {
        QUEST foundQuest = null;
        for (QUEST q : quests) {
            if (oldRoom.equals(q.completedOn) && recipeID.equals(q.getWantedId())) {
                foundQuest = q;
                break;
            }
        }
        if (foundQuest == null) {
            return false;
        }
        if (this.quests.remove(foundQuest)) {
            this.quests.add(questFactory.lost(foundQuest));
            this.changeListener.questLost(foundQuest);
            return true;
        }
        return false;
    }

    public @Nullable UUID getUUID() {
        if (quests.isEmpty()) {
            return null;
        }
        return quests.get(0).getUUID();
    }

    @Override
    public String toString() {
        return "QuestBatch[ID="+ batchUUID +"]{" +
                "quests=" + String.join(", ", quests.stream().map(Object::toString).toList()) +
                ", reward=" + reward +
                ", questFactory=" + questFactory +
                ", changeListener=" + changeListener +
                '}';
    }

    public void assignTo(@NotNull UUID owner) {
        for (QUEST q : quests) {
            q.uuid = owner;
        }
    }

    public void setReward(REWARD reward) {
        this.reward = reward;
    }

    public UUID getBatchUUID() {
        return batchUUID;
    }

    public interface ChangeListener<QUEST extends Quest<?, ?>> {
        void questCompleted(QUEST quest);

        void questBatchCompleted(QuestBatch<?, ?, ?, ?> quest);

        void questLost(QUEST quest);
    }
}
