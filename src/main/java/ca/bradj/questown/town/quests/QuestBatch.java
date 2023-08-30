package ca.bradj.questown.town.quests;

import ca.bradj.roomrecipes.core.Room;
import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

// Quests is a unit testable module for the quests of a town
public class QuestBatch<
        KEY,
        ROOM extends Room,
        QUEST extends Quest<KEY, ROOM>,
        REWARD extends Reward
        > {

    private final List<QUEST> quests = new ArrayList<>();
    protected REWARD reward;

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
    };

    QuestBatch(
            Quest.QuestFactory<KEY, ROOM, QUEST> qf,
            REWARD reward
    ) {
        this.questFactory = qf;
        this.reward = reward;
    }

    public void addChangeListener(ChangeListener<QUEST> changeListener) {
        this.changeListener = changeListener;
    }

    public Collection<KEY> getCompletedRecipeIDs() {
        return this.quests.stream().filter(Quest::isComplete).map(Quest::getWantedId).toList();
    }

    public void addNewQuest(KEY id) {
        this.quests.add(this.questFactory.newQuest(id));
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

    public boolean markRecipeAsConverted(
            KEY oldRecipeID,
            ROOM newRoom, KEY newRecipeID
    ) {
        Stream<QUEST> oldMatches = this.quests.stream()
                .filter(v -> oldRecipeID.equals(v.getWantedId()));
        Stream<QUEST> newMatches = this.quests.stream()
                .filter(v -> newRecipeID.equals(v.getWantedId()));
        Optional<QUEST> complete = oldMatches.filter(Quest::isComplete).findFirst();
        Optional<QUEST> incomplete = newMatches.filter(Quest::isComplete).findFirst();
        if (incomplete.isEmpty() || complete.isEmpty()) {
            return false;
        }
        QUEST oldQuest = complete.get();
        QUEST converted = incomplete.get();
        this.quests.remove(oldQuest);
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

    public interface ChangeListener<QUEST extends Quest<?, ?>> {
        void questCompleted(QUEST quest);

        void questBatchCompleted(QuestBatch<?, ?, ?, ?> quest);
    }
}
