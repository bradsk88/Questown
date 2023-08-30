package ca.bradj.questown.town.quests;

import ca.bradj.questown.Questown;
import ca.bradj.roomrecipes.core.Room;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class QuestBatches<
        KEY,
        ROOM extends Room,
        QUEST extends Quest<KEY, ROOM>,
        REWARD extends Reward,
        BATCH extends QuestBatch<KEY, ROOM, QUEST, REWARD>
> implements QuestBatch.ChangeListener<QUEST> {

    protected final List<BATCH> batches = new ArrayList<>();
    private QuestBatch.ChangeListener<QUEST> changeListener = new QuestBatch.ChangeListener<QUEST>() {
        @Override
        public void questCompleted(QUEST quest) {
            // No op by default
        }

        @Override
        public void questBatchCompleted(QuestBatch<?, ?, ?, ?> quest) {
            // No op by default
        }
    };

    public void initialize(ImmutableList<BATCH> bs) {
        if (!batches.isEmpty()) {
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
    public void questBatchCompleted(QuestBatch<?, ?, ?, ?> quest) {
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

    public void markRecipeAsComplete(ROOM room, KEY recipeId) {
        for (BATCH b : batches) {
            if (b.markRecipeAsComplete(room, recipeId)) {
                break;
            }
        }
    }

    public void markRecipeAsConverted(
            ROOM oldRoom, KEY oldRecipeID, ROOM newRoom, KEY newRecipeID) {
        for (BATCH b : batches) {
            if (b.markRecipeAsConverted(oldRecipeID, newRoom, newRecipeID)) {
                break;
            }
        }
    }

    public void markRecipeAsLost(ROOM oldRoom, KEY recipeID) {

    }
}
