package ca.bradj.questown.town.quests;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

// Quests is a unit testable module for the quests of a town
public class Quests<KEY, QUEST extends Quest<KEY>> {

    private final List<QUEST> quests = new ArrayList<>();
    private final Quest.QuestFactory<KEY, QUEST> questFactory;

    // TODO: Support multiple?
    private ChangeListener<QUEST> changeListener = new ChangeListener<QUEST>() {
        @Override
        public void questCompleted(QUEST quest) {
            // No op by default
        }
    };

    Quests(
            Quest.QuestFactory<KEY, QUEST> sc
    ) {
        this.questFactory = sc;
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

    void initialize(ImmutableList<QUEST> aqs) {
        if (this.quests.size() > 0) {
            throw new IllegalStateException("Quests already initialized");
        }
        this.quests.addAll(aqs);
    }

    public void markRecipeAsComplete(KEY recipe) {
        Stream<QUEST> matches = this.quests.stream().filter(v -> recipe.equals(v.getId()));
        Optional<QUEST> incomplete = matches.filter(v -> !v.isComplete()).findFirst();
        if (incomplete.isEmpty()) {
            return;
        }
        QUEST quest = incomplete.get();
        this.quests.remove(quest);
        QUEST updated = this.questFactory.withStatus(quest, Quest.QuestStatus.COMPLETED);
        this.quests.add(updated);
        this.changeListener.questCompleted(updated);
    }

    public interface ChangeListener<QUEST extends Quest<?>> {
        void questCompleted(QUEST quest);
    }
}
