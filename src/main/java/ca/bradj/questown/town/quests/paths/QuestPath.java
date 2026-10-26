package ca.bradj.questown.town.quests.paths;

import com.google.common.collect.ImmutableList;

/**
 * A curated quest path (plan `docs/plans/2026-10-24-002-feat-curated-quest-paths-plan.md`): batches
 * of quests a player opens after the onboarding trunk. A batch with no {@code opensAfter} is a
 * starter, posted at the fork; any other batch is posted once every batch it names is complete.
 * Loaded from {@code data/<ns>/questown_paths/<path>.json} ({@link QuestPathParser}).
 */
public record QuestPath(String id, ImmutableList<Batch> batches) {

    public record Batch(String id, ImmutableList<String> opensAfter, boolean arrival, ImmutableList<Step> quests) {
        public boolean isStarter() {
            return opensAfter.isEmpty();
        }
    }

    public enum Kind {
        ROOM, JOB_CHANGE, LOOT, SKILL
    }

    /** One quest: a room recipe id, a job id ({@code root/job}), or an item/tag id with a count. */
    public record Step(Kind kind, String id, int count) {
    }

    /** The tag a posted batch's quests carry: {@code <path id>/<batch id>}. */
    public String batchTag(Batch batch) {
        return id + "/" + batch.id();
    }
}
