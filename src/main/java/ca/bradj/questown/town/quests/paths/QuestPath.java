package ca.bradj.questown.town.quests.paths;

import com.google.common.collect.ImmutableList;

/** A curated quest path: batches of quests opened after onboarding. A batch with no {@code opensAfter} is a starter; any other posts once the batches it names are done. Loaded from {@code data/<ns>/questown_paths/}. */
public record QuestPath(String id, ImmutableList<Batch> batches) {

    public record Batch(String id, ImmutableList<String> opensAfter, boolean arrival, ImmutableList<Step> quests) {
        public boolean isStarter() {
            return opensAfter.isEmpty();
        }
    }

    public enum Kind {
        ROOM, JOB_CHANGE, LOOT, SKILL, LEVEL
    }

    /** One quest: a room recipe, a job ({@code root/job}), an item with a count, or a job root and the level to reach. */
    public record Step(Kind kind, String id, int count) {
    }

    /** The tag a posted batch's quests carry: {@code <path id>/<batch id>}. */
    public String batchTag(Batch batch) {
        return id + "/" + batch.id();
    }
}
