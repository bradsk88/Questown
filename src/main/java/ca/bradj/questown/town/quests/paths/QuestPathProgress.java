package ca.bradj.questown.town.quests.paths;

import com.google.common.collect.ImmutableList;

import java.util.Set;

/**
 * Where a town stands on one path, from the batch ids it has posted and completed. Starters post at
 * the fork; a later batch posts once every batch in its {@code opens_after} is complete. A path is
 * taken once a starter is complete, and finished once nothing is left to post and every posted
 * batch is complete. Untaken starters stay open; the garden follows the taken paths.
 */
public final class QuestPathProgress {

    private QuestPathProgress() {
    }

    public static ImmutableList<QuestPath.Batch> toPost(QuestPath path, Set<String> posted, Set<String> complete) {
        return path.batches().stream()
                .filter(b -> !posted.contains(b.id()))
                .filter(b -> complete.containsAll(b.opensAfter()))
                .collect(ImmutableList.toImmutableList());
    }

    public static boolean taken(QuestPath path, Set<String> complete) {
        return path.batches().stream().anyMatch(b -> b.isStarter() && complete.contains(b.id()));
    }

    public static boolean finished(QuestPath path, Set<String> posted, Set<String> complete) {
        return toPost(path, posted, complete).isEmpty() && complete.containsAll(posted);
    }
}
