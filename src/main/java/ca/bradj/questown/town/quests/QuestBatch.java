package ca.bradj.questown.town.quests;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

// Quests is a unit testable module for the quests of a town
public class QuestBatch<KEY, QUEST extends Quest<KEY>> {

    private final List<QUEST> quests = new ArrayList<>();
    protected Reward reward;

    private final Quest.QuestFactory<KEY, QUEST> questFactory;

    // TODO: Support multiple?
    private ChangeListener<QUEST> changeListener = new ChangeListener<>() {
        @Override
        public void questCompleted(QUEST quest) {
            // No op by default
        }

        @Override
        public void questBatchCompleted(
                QuestBatch<?, ?> quest
        ) {
            // No op by default
        }
    };

    QuestBatch(
            Quest.QuestFactory<KEY, QUEST> qf,
            Reward reward
    ) {
        this.questFactory = qf;
        this.reward = reward;
    }

    public void addChangeListener(ChangeListener<QUEST> changeListener) {
        this.changeListener = changeListener;
    }

    public Collection<KEY> getCompleted() {
        return this.quests.stream().filter(Quest::isComplete).map(Quest::getId).toList();
    }

    public void addNewQuest(KEY id) {
        this.quests.add(this.questFactory.newQuest(id));
    }

    public ImmutableList<QUEST> getAll() {
        return ImmutableList.copyOf(this.quests);
    }

    void initialize(
            ImmutableList<QUEST> aqs,
            Reward reward
    ) {
        if (this.quests.size() > 0) {
            throw new IllegalStateException("Quests already initialized");
        }
        this.quests.addAll(aqs);
        this.reward = reward;
    }

    public boolean markRecipeAsComplete(KEY recipe) {
        Stream<QUEST> matches = this.quests.stream().filter(v -> recipe.equals(v.getId()));
        Optional<QUEST> incomplete = matches.filter(v -> !v.isComplete()).findFirst();
        if (incomplete.isEmpty()) {
            return false;
        }
        QUEST quest = incomplete.get();
        this.quests.remove(quest);
        QUEST updated = this.questFactory.withStatus(quest, Quest.QuestStatus.COMPLETED);
        this.quests.add(updated);
        this.changeListener.questCompleted(updated);

        matches = this.quests.stream().filter(v -> recipe.equals(v.getId()));
        incomplete = matches.filter(v -> !v.isComplete()).findFirst();
        if (incomplete.isEmpty()) {
            this.reward.claim();
            this.changeListener.questBatchCompleted(this);
        }
        return true;
    }

    public static <QUEST extends Quest<?>> Stream<QUEST> stream(QuestBatch<?, QUEST> batch) {
        return batch.quests.stream();
    }

    public interface ChangeListener<QUEST extends Quest<?>> {
        void questCompleted(QUEST quest);

        void questBatchCompleted(QuestBatch<?, ?> quest);
    }
}
