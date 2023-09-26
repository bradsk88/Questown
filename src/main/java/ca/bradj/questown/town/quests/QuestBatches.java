package ca.bradj.questown.town.quests;

import ca.bradj.questown.Questown;
import ca.bradj.roomrecipes.core.Room;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

        @Override
        public void questLost(QUEST quest) {
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

    @Override
    public void questLost(QUEST quest) {
        this.changeListener.questLost(quest);
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

    public void markRecipeAsComplete(
            ROOM room,
            KEY recipeId
    ) {
        for (BATCH b : batches) {
            if (b.markRecipeAsComplete(room, recipeId)) {
                break;
            }
        }
    }

    public ImmutableMap<QUEST, REWARD> getAllWithRewards() {
        ImmutableMap.Builder<QUEST, REWARD> b = ImmutableMap.builder();
        this.batches.forEach(v -> v.getAll().forEach(z -> b.put(z, v.reward)));
        return b.build();
    }

    public Map<QUEST, REWARD> getAllForVillagerWithRewards(UUID uuid) {
        ImmutableMap.Builder<QUEST, REWARD> b = ImmutableMap.builder();
        this.batches.stream()
                .filter(v -> v.getAll().stream().allMatch(z -> z.uuid.equals(uuid)))
                .forEach(v -> v.getAll().forEach(z -> b.put(z, v.reward)));
        return b.build();
    }

    public interface ConversionFunc {
        void apply();
    }

    public void markRecipeAsConverted(
            ROOM room,
            KEY oldRecipeID,
            KEY newRecipeID
    ) {
        ConversionFunc oldQuest = null;
        ConversionFunc newQuest = null;

        for (BATCH b : batches) {
            if (oldQuest == null) {
                QUEST match = b.findMatch(room, oldRecipeID);
                if (match != null) {
                    oldQuest = () -> b.markConsumed(match);
                }
            }
            if (newQuest == null) {
                if (b.canMarkRecipeAsConverted(oldRecipeID, newRecipeID)) {
                    newQuest = () -> b.markRecipeAsConverted(room, oldRecipeID, newRecipeID);
                }
            }
            if (oldQuest != null && newQuest != null) {
                oldQuest.apply();
                newQuest.apply();
                return;
            }
        }
    }

    public void markRecipeAsLost(
            ROOM oldRoom,
            KEY recipeID
    ) {
        for (BATCH b : batches) {
            if (b.markRecipeAsLost(oldRoom, recipeID)) {
                return;
            }
        }
    }

    public void changeRoomOnly(
            ROOM oldRoom,
            ROOM newRoom
    ) {
        for (BATCH b : batches) {
            b.changeRoomOnly(oldRoom, newRoom);
        }
    }
}
