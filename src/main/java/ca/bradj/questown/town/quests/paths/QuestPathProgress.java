package ca.bradj.questown.town.quests.paths;

import com.google.common.collect.ImmutableList;

import java.util.Set;

/** Where a town is on one path. A path is taken once a starter is done, and finished once everything is posted and done. */
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
