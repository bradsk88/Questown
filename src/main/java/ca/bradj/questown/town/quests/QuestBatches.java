package ca.bradj.questown.town.quests;

import ca.bradj.questown.QT;
import ca.bradj.questown.Questown;
import ca.bradj.questown.town.special.SpecialQuests;
import ca.bradj.roomrecipes.core.Room;
import com.google.common.collect.ImmutableList;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public class QuestBatches<
        KEY,
        ROOM extends Room,
        QUEST extends Quest<KEY, ROOM>,
        REWARD extends Reward,
        BATCH extends QuestBatch<KEY, ROOM, QUEST, REWARD>
        > implements QuestBatch.ChangeListener<QUEST> {

    private static final Marker marker = MarkerManager.getMarker("Batches");

    protected final List<BATCH> batches = new ArrayList<>();
    private final Factory<BATCH, REWARD> factory;
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

    public boolean decline(BATCH b) {
        return batches.remove(b);
    }

    public interface Factory<BATCH, REWARD> {
        BATCH getNew(UUID batchUUID, UUID owner, REWARD r);
    }

    public interface VillagerProvider<R extends Room> {
        UUID getRandomVillager();
        boolean isVillagerMissing(UUID uuid);

        Optional<R> assignToFarm(UUID ownerUUID);

        Optional<R> getBiggestFarm();
    }


    public QuestBatches(Factory<BATCH, REWARD> factory) {
        this.factory = factory;
    }

    public void initialize(VillagerProvider<ROOM> villagers, ImmutableList<BATCH> bs) {
        if (!batches.isEmpty()) {
            Questown.LOGGER.error("QuestBatches were initialized twice :(");
        }

        ImmutableList<BATCH> list = filterOutDuplicateCompletion(villagers, bs);

        batches.addAll(list);
        for (BATCH b : batches) {
            b.addChangeListener(this);
        }
    }

    private ImmutableList<BATCH> filterOutDuplicateCompletion(
            VillagerProvider villagers,
            ImmutableList<BATCH> bs) {
        Set<QUEST> completedQuests = bs.stream()
                .flatMap(v -> v.getAll().stream())
                .filter(Quest::isComplete)
                .peek(q -> q.uuid = coerceUUID(villagers, q.getUUID()))
                .collect(Collectors.toSet());

        ImmutableList.Builder<BATCH> bld = ImmutableList.builder();
        bs.forEach(v -> {
            final UUID owner = coerceUUID(villagers, v.getUUID());
            BATCH e = this.emptyBatch(v.getBatchUUID(), owner, v.reward);
            ImmutableList.Builder<QUEST> eqb = ImmutableList.builder();
            v.getAll().forEach(q -> {
                e.addNewQuest(owner, q.getWantedId());
                if (completedQuests.contains(q)) {
                    e.markRecipeAsComplete(q.completedOn, q.getWantedId());
                    completedQuests.remove(q);
                }
                eqb.add(q);
            });
            bld.add(e);
        });
        return bld.build();
    }

    @Nullable
    private static <KEY, ROOM extends Room, QUEST extends Quest<KEY, ROOM>, REWARD extends Reward, BATCH extends QuestBatch<KEY, ROOM, QUEST, REWARD>> UUID coerceUUID(
            VillagerProvider villagers,
            @Nullable UUID owner
    ) {
        if (owner != null && villagers.isVillagerMissing(owner)) {
            UUID newOwner = villagers.getRandomVillager();
            if (newOwner == null) {
                // FIXME: This will alway happen because the flag gets initialized before entities
                QT.LOGGER.warn("Could not repair quest belonging to {} because no other villagers exist", owner);
            } else {
                QT.LOGGER.warn("Replacing missing villager {} with {}", owner, newOwner);
                owner = newOwner;
            }
        }
        return owner;
    }

    private BATCH emptyBatch(UUID batchUUID, UUID owner, REWARD reward) {
        return this.factory.getNew(batchUUID, owner, reward);
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
            if (b.getAll().stream()
                    .filter(Quest::isComplete)
                    .filter(v -> !SpecialQuests.CAMPFIRE.equals(v.getWantedId()))
                    .filter(v -> recipeId.equals(v.getWantedId()))
                    .anyMatch(v -> room.equals(v.completedOn))
            ) {
                QT.QUESTS_LOGGER.debug(marker, "Quest was already marked complete: {} for door {}", recipeId, room.doorPos);
                return;
            }
        }
        for (BATCH b : batches) {
            if (b.markRecipeAsComplete(room, recipeId)) {
                break;
            }
        }
    }

    public ImmutableList<HashMap.SimpleEntry<QUEST, REWARD>> getAllWithRewards() {
        ImmutableList.Builder<HashMap.SimpleEntry<QUEST, REWARD>> b = ImmutableList.builder();
        this.batches.forEach(v -> v.getAll().forEach(z -> b.add(new AbstractMap.SimpleEntry<>(z, v.reward))));
        return b.build();
    }

    public List<HashMap.SimpleEntry<QUEST, REWARD>> getAllForVillagerWithRewards(UUID uuid) {
        ImmutableList.Builder<HashMap.SimpleEntry<QUEST, REWARD>> b = ImmutableList.builder();
        this.batches.stream()
                .filter(v -> v.getAll().stream().allMatch(z -> uuid.equals(z.uuid)))
                .forEach(v -> v.getAll().forEach(z -> b.add(new HashMap.SimpleEntry<>(z, v.reward))));
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
