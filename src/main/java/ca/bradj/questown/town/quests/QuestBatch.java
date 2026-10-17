package ca.bradj.questown.town.quests;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.VillagerUUID;
import ca.bradj.roomrecipes.core.Room;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

// Quests is a unit testable module for the quests of a town
public class QuestBatch<KEY, ROOM extends Room, QUEST extends Quest<KEY, ROOM>, REWARD extends Reward> {

    private final List<QUEST> quests = new ArrayList<>();
    private final Quest.QuestFactory<KEY, ROOM, QUEST> questFactory;
    @NotNull
    protected REWARD reward;

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

    /** The quest added most recently (the load replay's just-rebuilt quest). */
    QUEST newestQuest() {
        return this.quests.get(this.quests.size() - 1);
    }

    public static <R extends Room, Q extends Quest<?, R>> Stream<Q> stream(QuestBatch<?, R, Q, ?> batch) {
        return batch.quests.stream();
    }

    private static @Nullable UUID dep(@Nullable VillagerUUID ownerId) {
        return VillagerUUID.get(ownerId);
    }

    public void addChangeListener(ChangeListener<QUEST> changeListener) {
        this.changeListener = changeListener;
    }

    public Collection<KEY> getCompletedRecipeIDs() {
        return this.quests.stream().filter(Quest::isComplete).map(Quest::getWantedId).toList();
    }

    public void addNewQuest(
            @Nullable VillagerUUID ownerId,
            KEY id
    ) {
        this.quests.add(this.questFactory.newQuest(dep(ownerId), id));
    }
    public void addJobChangeQuest(
            KEY id
    ) {
        this.quests.add(this.questFactory.newJobQuest(id));
    }

    public void addJobUnlockQuest(
            KEY id
    ) {
        this.quests.add(this.questFactory.newJobUnlockQuest(id));
    }

    public void addNewUpgradeQuest(
            @Nullable VillagerUUID ownerId,
            KEY fromID,
            KEY toID
    ) {
        this.quests.add(this.questFactory.newUpgradeQuest(dep(ownerId), fromID, toID));
    }

    public void addItemQuest(
            @Nullable VillagerUUID ownerId,
            KEY itemId,
            int count
    ) {
        this.quests.add(this.questFactory.newItemQuest(dep(ownerId), itemId, count));
    }

    public void addLootQuest(
            @Nullable VillagerUUID ownerId,
            KEY itemId,
            int count
    ) {
        this.quests.add(this.questFactory.newLootQuest(dep(ownerId), itemId, count));
    }

    /**
     * The day-2 "post your first work request" quest. Rebuild paths must use this -
     * {@link #addNewQuest} would produce a ROOM-typed recipe quest with the raw id.
     */
    public void addFirstWorkRequestQuest(@Nullable VillagerUUID ownerId) {
        this.quests.add(this.questFactory.newFirstWorkRequestQuest(dep(ownerId)));
    }

    /** A CONCURRENT_JOBS quest; the job list rides on the quest, not the wanted id. */
    public void addConcurrentJobsQuest(@Nullable VillagerUUID ownerId, Collection<KEY> jobIds) {
        this.quests.add(this.questFactory.newConcurrentJobsQuest(dep(ownerId), jobIds));
    }

    public ImmutableList<QUEST> getAll() {
        return ImmutableList.copyOf(this.quests);
    }

    void initialize(
            @Nullable UUID batchUUID,
            Collection<QUEST> aqs,
            REWARD reward
    ) {
        if (!this.quests.isEmpty()) {
            throw new IllegalStateException("Quests already initialized");
        }
        this.batchUUID = batchUUID;
        this.quests.addAll(aqs.stream().peek(v -> v.batchUUID = batchUUID).toList());
        this.reward = reward;
    }

    public boolean markRecipeAsComplete(
            ROOM room,
            KEY recipe
    ) {
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

    /**
     * Load-time replay only. Re-mark a persisted completed quest complete without claiming the
     * batch's reward. Completion and reward application are synchronous, so a completed quest
     * read back from disk implies the reward is already applied (it is restored with its
     * applied flag) - re-claiming through {@link #markRecipeAsComplete(ROOM, KEY)} would trip
     * the once-guard on every load of a post-onboarding world (the 2026-10-13 "Refusing to
     * apply reward more than once" load noise; ticket
     * .scratch/day1-playtest-2026-10-11/load-time-reward-reclaim-error-noise.md). No
     * listener events fire either: the rebuild is pure state re-establishment, not a new
     * completion (no chat broadcast, no batch-completion side effects on load).
     */
    public boolean markRecipeAsCompleteForReplay(@Nullable ROOM room, @Nullable KEY recipe) {
        Stream<QUEST> matches = this.quests.stream().filter(v -> recipe.equals(v.getWantedId()));
        Optional<QUEST> incomplete = matches.filter(v -> !v.isComplete()).findFirst();
        if (incomplete.isEmpty()) {
            return false;
        }
        QUEST quest = incomplete.get();
        this.quests.remove(quest);
        QUEST updated = this.questFactory.completed(room, quest);
        this.quests.add(updated);
        return true;
    }

    public boolean canMarkRecipeAsConverted(
            KEY oldRecipeID,
            KEY newRecipeID
    ) {
        Stream<QUEST> newMatches = this.quests.stream().filter(v -> newRecipeID.equals(v.getWantedId()));
        Optional<QUEST> incomplete = newMatches.filter(Predicate.not(Quest::isComplete)).findFirst();
        return incomplete.isPresent() && incomplete.get().fromRecipeID().map(v -> v.equals(oldRecipeID)).orElse(false);
    }

    public boolean markRecipeAsConverted(
            ROOM newRoom,
            KEY oldRecipeID,
            KEY newRecipeID
    ) {
        Stream<QUEST> newMatches = this.quests.stream().filter(v -> newRecipeID.equals(v.getWantedId()));
        Optional<QUEST> incomplete = newMatches.filter(Predicate.not(Quest::isComplete)).findFirst();
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

    public REWARD getReward() {
        return reward;
    }

    public void setReward(REWARD reward) {
        this.reward = reward;
    }

    public int size() {
        return this.quests.size();
    }

    public void changeRoomOnly(
            ROOM oldRoom,
            ROOM newRoom
    ) {
        List<QUEST> snapshot = ImmutableList.copyOf(this.quests);
        this.quests.clear();
        this.quests.addAll(snapshot.stream().peek(v -> {
            if (oldRoom.equals(v.completedOn)) {
                QT.QUESTS_LOGGER.debug(
                        "Quest completion room updated after room size change. {} -> {}",
                        oldRoom,
                        newRoom
                );
                v.completedOn = newRoom;
            }
        }).toList());
    }

    public @Nullable QUEST findMatch(
            ROOM room,
            KEY oldRecipeID
    ) {
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

    public boolean markRecipeAsLost(
            @NotNull ROOM oldRoom,
            @NotNull KEY recipeID
    ) {
        QUEST foundQuest = null;
        for (QUEST q : quests) {
            // A complete quest is perma-done (campfire semantics): re-typing its
            // room (e.g. the empty room gains a chest and becomes a store room)
            // must not lose it. 2026-10-14 playtest: the empty-room quest lost
            // and re-completed in one tick, double-firing the batch reward.
            if (oldRoom.equals(q.completedOn)
                    && recipeID.equals(q.getWantedId())
                    && !q.isComplete()) {
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

    /**
     * Withdraw a quest outright (no placeholder) and notify the listener. The JOB_UNLOCK
     * path uses this when its stuck root stops mapping to a viable producer: the
     * lost()-placeholder path re-typed the quest as an open room quest that no gate,
     * UI or completion path ever cleared (the handle-accumulation half of the
     * JOB_UNLOCK ping-pong, .scratch/mat-gated-arrival/job-unlock-quest-spam.md).
     */
    public boolean withdrawQuest(QUEST quest) {
        if (!this.quests.remove(quest)) {
            return false;
        }
        this.changeListener.questLost(quest);
        return true;
    }

    public @Nullable VillagerUUID getUUID() {
        if (quests.isEmpty()) {
            return null;
        }
        return quests.get(0).getUUID();
    }

    @Override
    public String toString() {
        return "QuestBatch[ID=" + batchUUID + "]{" + "quests=" + String.join(
                ", ",
                quests.stream().map(Object::toString).toList()
        ) + ", reward=" + reward + ", questFactory=" + questFactory + ", changeListener=" + changeListener + '}';
    }

    public void assignTo(@NotNull VillagerUUID owner) {
        for (QUEST q : quests) {
            q.ownerUUID = owner;
        }
    }

    public UUID getBatchUUID() {
        return batchUUID;
    }

    public @Nullable String getCompletionMessage() {
        return null;
    }

    public interface ChangeListener<QUEST extends Quest<?, ?>> {
        void questCompleted(QUEST quest);

        void questBatchCompleted(QuestBatch<?, ?, ?, ?> quest);

        void questLost(QUEST quest);
    }
}
