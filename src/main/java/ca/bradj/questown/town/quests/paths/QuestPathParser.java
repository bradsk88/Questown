package ca.bradj.questown.town.quests.paths;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Reads one path file. Each quest is an object with exactly one kind key ({@code room},
 * {@code job_change}, {@code loot}, {@code skill}) and, for {@code loot}, a {@code count}. Throws
 * {@link IllegalArgumentException} naming the field on a malformed file.
 */
public final class QuestPathParser {

    private QuestPathParser() {
    }

    public static QuestPath parse(JsonObject json) {
        String id = string(json, "id");
        ImmutableList.Builder<QuestPath.Batch> batches = ImmutableList.builder();
        for (JsonElement e : array(json, "batches")) {
            batches.add(batch(id, e.getAsJsonObject()));
        }
        return new QuestPath(id, batches.build());
    }

    private static QuestPath.Batch batch(String pathId, JsonObject json) {
        String id = string(json, "id");
        ImmutableList.Builder<String> opensAfter = ImmutableList.builder();
        if (json.has("opens_after")) {
            array(json, "opens_after").forEach(e -> opensAfter.add(e.getAsString()));
        }
        boolean arrival = json.has("arrival") && json.get("arrival").getAsBoolean();
        ImmutableList.Builder<QuestPath.Step> quests = ImmutableList.builder();
        for (JsonElement e : array(json, "quests")) {
            quests.add(step(pathId + "/" + id, e.getAsJsonObject()));
        }
        return new QuestPath.Batch(id, opensAfter.build(), arrival, quests.build());
    }

    private static QuestPath.Step step(String where, JsonObject json) {
        if (json.has("room")) {
            return new QuestPath.Step(QuestPath.Kind.ROOM, string(json, "room"), 1);
        }
        if (json.has("job_change")) {
            return new QuestPath.Step(QuestPath.Kind.JOB_CHANGE, string(json, "job_change"), 1);
        }
        if (json.has("skill")) {
            return new QuestPath.Step(QuestPath.Kind.SKILL, string(json, "skill"), 1);
        }
        if (json.has("loot")) {
            int count = json.has("count") ? json.get("count").getAsInt() : 1;
            return new QuestPath.Step(QuestPath.Kind.LOOT, string(json, "loot"), count);
        }
        throw new IllegalArgumentException(where + ": a quest needs one of room, job_change, skill, loot: " + json);
    }

    private static String string(JsonObject json, String key) {
        if (!json.has(key)) {
            throw new IllegalArgumentException("missing \"" + key + "\" in " + json);
        }
        return json.get(key).getAsString();
    }

    private static JsonArray array(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonArray()) {
            throw new IllegalArgumentException("missing array \"" + key + "\" in " + json);
        }
        return json.getAsJsonArray(key);
    }
}
