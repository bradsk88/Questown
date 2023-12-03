package ca.bradj.questown.town.quests;

import ca.bradj.roomrecipes.core.Room;
import ca.bradj.roomrecipes.core.space.Position;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class Quest<KEY, ROOM extends Room> {

    @Nullable protected UUID uuid;
    protected UUID batchUUID;
    protected KEY recipeId;
    protected QuestStatus status;
    protected @Nullable ROOM completedOn;
    private @Nullable KEY fromRecipeID;

    Quest() {
        this(null, null, null, null);
    }

    @Override
    public String toString() {
        return "Quest{" +
                "uuid=" + uuid +
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
        return String.format("Quest{id=%s, owner=%s, on=%s, from=%s", recipeId, uuid, doorPos, fromRecipeID);
    }

    protected Quest(UUID batchUUID, @Nullable UUID ownerId, KEY recipe, @Nullable KEY oldRecipe) {
        this.batchUUID = batchUUID;
        this.uuid = ownerId;
        this.recipeId = recipe;
        this.status = QuestStatus.ACTIVE;
        this.fromRecipeID = oldRecipe;
    }

    public KEY getWantedId() {
        return recipeId;
    }

    public boolean isComplete() {
        return QuestStatus.COMPLETED.equals(status);
    }

    public QuestStatus getStatus() {
        return status;
    }

    public UUID getUUID() {
        return this.uuid;
    }

    public void initialize(
            UUID batchUUID,
            UUID uuid,
            KEY recipeId,
            QuestStatus status,
            @Nullable ROOM completedOn,
            @Nullable KEY fromRecipeID
    ) {
        this.batchUUID = batchUUID;
        this.uuid = uuid;
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
                default ->
                    throw new IllegalStateException("Unexpected status: " + status);
            };
        }

        public String asString() {
            return str;
        }
    }

    interface QuestFactory<KEY, ROOM extends Room, QUEST extends Quest<KEY, ROOM>> {
        QUEST newQuest(
                @Nullable UUID ownerId, KEY recipeId
        );
        QUEST newUpgradeQuest(
                @Nullable UUID ownerId, KEY oldRecipeId, KEY newRecipeId
        );
        QUEST completed(
                ROOM room,
                QUEST input
        );

        QUEST lost(QUEST foundQuest);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Quest<?, ?> quest = (Quest<?, ?>) o;
        return Objects.equals(uuid, quest.uuid) && Objects.equals(
                recipeId,
                quest.recipeId
        ) && status == quest.status && Objects.equals(completedOn, quest.completedOn) && Objects.equals(
                fromRecipeID,
                quest.fromRecipeID
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid, recipeId, status, completedOn, fromRecipeID);
    }
}
