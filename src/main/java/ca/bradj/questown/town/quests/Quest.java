package ca.bradj.questown.town.quests;

import java.util.UUID;

public class Quest<KEY> {

    protected UUID uuid;
    protected KEY recipeId;
    protected QuestStatus status;

    Quest() {
        this(null);
    }

    public Quest(KEY recipe) {
        this.uuid = UUID.randomUUID();
        this.recipeId = recipe;
        this.status = QuestStatus.ACTIVE;
    }

    public KEY getId() {
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
            QuestStatus status
    ) {
        this.uuid = uuid;
        this.recipeId = recipeId;
        this.status = status;
    }

    public enum QuestStatus {
        UNSET(""),
        ACTIVE("active"),
        COMPLETED("completed");

        private final String str;

        QuestStatus(String str) {
            this.str = str;
        }
    }

    interface QuestFactory<KEY, QUEST extends Quest<KEY>> {
        QUEST newQuest(
                KEY recipeId
        );
        QUEST withStatus(
                QUEST input,
                Quest.QuestStatus status
        );
    }


}
