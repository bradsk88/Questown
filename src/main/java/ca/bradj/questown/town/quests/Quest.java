package ca.bradj.questown.town.quests;

import ca.bradj.roomrecipes.core.Room;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

public class Quest<KEY, ROOM extends Room> {

    protected UUID uuid;
    protected KEY recipeId;
    protected QuestStatus status;
    protected ROOM completedOn;
    private KEY fromRecipeID;

    Quest() {
        this(null);
    }

    public Quest(KEY recipe) {
        this.uuid = UUID.randomUUID();
        this.recipeId = recipe;
        this.status = QuestStatus.ACTIVE;
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
            UUID uuid,
            KEY recipeId,
            QuestStatus status,
            @Nullable ROOM completedOn
    ) {
        this.uuid = uuid;
        this.recipeId = recipeId;
        this.status = status;
        this.completedOn = completedOn;
    }

    public Optional<KEY> fromRecipeID() {
        return Optional.ofNullable(this.fromRecipeID);
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
                KEY recipeId
        );
        QUEST completed(
                ROOM room,
                QUEST input
        );
    }


}
