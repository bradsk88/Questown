package ca.bradj.questown.town.quests;

import ca.bradj.questown.core.VillagerUUID;
import ca.bradj.roomrecipes.core.Room;
import ca.bradj.roomrecipes.core.space.Position;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class Quest<KEY, ROOM extends Room> implements Completable {

    final UUID selfUUID = UUID.randomUUID(); // Mostly just for equality
    @Nullable
    protected VillagerUUID ownerUUID;
    protected UUID batchUUID;
    protected KEY recipeId;
    protected QuestStatus status;
    protected @Nullable ROOM completedOn;
    private QuestType type;
    private int count;
    @Nullable
    KEY fromRecipeID;
    @Nullable
    private String flavorText;

    // Onboarding quests complete on any room that contains the wanted recipe; others need an exact match.
    private boolean onboarding;
    // Only procedural batches can be discarded.
    private boolean discardable;
    // The producer a top-need room quest was posted for, or null.
    @Nullable
    private String topNeedProducer;

    Quest() {
        this(null, null, null, null, null, 1);
    }

    /** The room this quest completed on, or null. */
    public @Nullable ROOM completedOn() {
        return completedOn;
    }

    @Override
    public String toString() {
        return "Quest{" +
                "selfUUID=" + selfUUID +
                "ownerUUID=" + ownerUUID +
                ", recipeId=" + recipeId +
                ", status=" + status +
                ", completedOn=" + completedOn +
                ", fromRecipeID=" + fromRecipeID +
                '}';
    }

    public String toShortString() {
        Position doorPos = null;
        if (completedOn != null) {
            doorPos = completedOn.doorPos;
        }
        return String.format("Quest{id=%s, owner=%s, on=%s, from=%s", recipeId, ownerUUID, doorPos, fromRecipeID);
    }

    protected Quest(
            UUID batchUUID,
            @Nullable VillagerUUID ownerId,
            KEY recipe,
            @Nullable KEY oldRecipe,
            QuestType questType,
            int count
    ) {
        this.batchUUID = batchUUID;
        this.ownerUUID = ownerId;
        this.recipeId = recipe;
        this.status = QuestStatus.ACTIVE;
        this.fromRecipeID = oldRecipe;
        this.type = questType;
        this.count = count;
    }

    public KEY getWantedId() {
        return recipeId;
    }

    @Override
    public boolean isComplete() {
        return QuestStatus.COMPLETED.equals(status);
    }

    public QuestStatus getStatus() {
        return status;
    }

    public VillagerUUID getUUID() {
        return this.ownerUUID;
    }

    public VillagerUUID getOwner() {
        return this.ownerUUID;
    }

    public void setOwner(VillagerUUID uuid) {
        this.ownerUUID = uuid;
    }

    public void initialize(
            VillagerUUID uuid,
            QuestType type,
            int count,
            KEY recipeId,
            QuestStatus status,
            @Nullable ROOM completedOn,
            @Nullable KEY fromRecipeID
    ) {
        // Batch ID on this quest is initialized by the batch itself.
        this.ownerUUID = uuid;
        this.type = type;
        this.count = count;
        this.recipeId = recipeId;
        this.status = status;
        this.completedOn = completedOn;
        this.fromRecipeID = fromRecipeID;
    }

    public Optional<KEY> fromRecipeID() {
        return Optional.ofNullable(this.fromRecipeID);
    }

    public UUID getBatchUUID() {
        return batchUUID;
    }

    public QuestType getType() {
        return type;
    }

    /** A CONCURRENT_JOBS quest's job ids; empty for other types. */
    public Collection<KEY> getConcurrentJobIds() {
        return Collections.emptyList();
    }

    public int getCountNeeded() {
        return count;
    }

    @Nullable
    public String getFlavorText() {
        return flavorText;
    }

    public void setFlavorText(@Nullable String flavorText) {
        this.flavorText = flavorText;
    }

    public boolean isOnboarding() {
        return onboarding;
    }

    public void setOnboarding(boolean onboarding) {
        this.onboarding = onboarding;
    }

    public boolean isDiscardable() {
        return discardable;
    }

    public void setDiscardable(boolean discardable) {
        this.discardable = discardable;
    }

    public Optional<String> topNeedProducer() {
        return Optional.ofNullable(topNeedProducer);
    }

    public void setTopNeedProducer(@Nullable String producerRoot) {
        this.topNeedProducer = producerRoot;
    }

    /** Copies what a rebuild on load doesn't: the onboarding flag and the flavor text. */
    protected void carrySavedStateFrom(Quest<?, ?> saved) {
        this.onboarding = saved.onboarding;
        this.discardable = saved.discardable;
        this.flavorText = saved.flavorText;
        this.topNeedProducer = saved.topNeedProducer;
        this.pathBatch = saved.pathBatch;
    }

    public enum QuestStatus {
        UNSET(""),
        ACTIVE("active"),
        COMPLETED("completed"),
        LOST("lost");

        private final String str;

        QuestStatus(String str) {
            this.str = str;
        }

        public static QuestStatus fromString(String status) {
            return switch (status) {
                case "active" -> ACTIVE;
                case "completed" -> COMPLETED;
                case "lost" -> LOST;
                default -> throw new IllegalStateException("Unexpected status: " + status);
            };
        }

        public String asString() {
            return str;
        }
    }

    public enum QuestType {
        ITEM, ROOM, JOB_CHANGE, CONCURRENT_JOBS, UNKNOWN;
    }

    protected interface QuestFactory<KEY, ROOM extends Room, QUEST extends Quest<KEY, ROOM>> {
        QUEST newQuest(
                @Nullable UUID ownerId,
                KEY recipeId
        );

        QUEST newUpgradeQuest(
                @Nullable UUID ownerId,
                KEY oldRecipeId,
                KEY newRecipeId
        );

        QUEST newItemQuest(
                @Nullable UUID ownerId,
                KEY itemId,
                int count
        );

        QUEST newLootQuest(
                @Nullable UUID ownerId,
                KEY itemId,
                int count
        );

        QUEST newJobUnlockQuest(KEY id);

        /** Day 2's first-work-request quest. Don't build it with {@link #newQuest}. */
        QUEST newFirstWorkRequestQuest(@Nullable UUID ownerId);

        /** A CONCURRENT_JOBS quest. Don't build it with {@link #newQuest}: the job list would be lost. */
        QUEST newConcurrentJobsQuest(@Nullable UUID ownerId, Collection<KEY> jobIds);

        QUEST completed(
                ROOM room,
                QUEST input
        );

        QUEST lost(QUEST foundQuest);

        QUEST newJobQuest(KEY id);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Quest<?, ?> quest = (Quest<?, ?>) o;
        return equals(false, this, quest);
    }

    public static <Q extends Quest<?, ?>> boolean equals(
            boolean ignoreSelfUUID,
            Q one,
            Q another
    ) {
        boolean eqUUID = Objects.equals(one.selfUUID, another.selfUUID);
        if (!eqUUID && !ignoreSelfUUID) {
            return false;
        }
        return Objects.equals(
                one.ownerUUID,
                another.ownerUUID
        ) && Objects.equals(one.batchUUID, another.batchUUID) && Objects.equals(
                one.recipeId,
                another.recipeId
        ) && one.status == another.status && Objects.equals(one.completedOn, another.completedOn) && Objects.equals(
                one.fromRecipeID,
                another.fromRecipeID
        );
    }

    @Override
    public int hashCode() {
        return Quest.hashCode(false, this);
    }

    public static int hashCode(
            boolean ignoreOwnUUID,
            Quest<?, ?> q
    ) {
        @Nullable UUID sUU = q.selfUUID;
        if (ignoreOwnUUID) {
            sUU = null;
        }
        return Objects.hash(sUU, q.ownerUUID, q.batchUUID, q.recipeId, q.status, q.completedOn, q.fromRecipeID);
    }
}
