package ca.bradj.questown.town.quests;

import ca.bradj.questown.Questown;
import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.List;

public class QuestBatches<KEY, QUEST extends Quest<KEY>, REWARD
        extends Reward, BATCH extends QuestBatch<KEY, QUEST, REWARD>>
        implements QuestBatch.ChangeListener<QUEST> {

    protected final List<BATCH> batches = new ArrayList<>();
    private QuestBatch.ChangeListener<QUEST> changeListener = new QuestBatch.ChangeListener<QUEST>() {
        @Override
        public void questCompleted(QUEST quest) {
            // No op by default
        }

        @Override
        public void questBatchCompleted(QuestBatch<?, ?, ?> quest) {
            // No op by default
        }
    };

    public void initialize(ImmutableList<BATCH> bs) {
        if (batches.size() > 0) {
            Questown.LOGGER.error("QuestBatches were initialized twice :(");
        }
        batches.addAll(bs);
        for (BATCH b : batches) {
            b.addChangeListener(this);
        }
    }

    @Override
    public void questCompleted(QUEST quest) {
        this.changeListener.questCompleted(quest);
    }

    @Override
    public void questBatchCompleted(QuestBatch<?, ?, ?> quest) {
        this.changeListener.questBatchCompleted(quest);
    }

    public void addChangeListener(QuestBatch.ChangeListener<QUEST> listener) {
        this.changeListener = listener;
    }

    public void add(BATCH qb) {
        this.batches.add(qb);
        qb.addChangeListener(this);
    }

    public ImmutableList<QUEST> getAll() {
        return ImmutableList.copyOf(this.batches.stream().flatMap(QuestBatch::stream).toList());
    }

    public void markRecipeAsComplete(KEY recipeId) {
        for (BATCH b : batches) {
            if (b.markRecipeAsComplete(recipeId)) {
                break;
            }
        }
    }
}
