package ca.bradj.questown.town.quests.paths;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The structure rules a path file must meet: unique batch ids, {@code opens_after} names a batch
 * of the same path, no cycle, at least one starter, quests in every batch, positive counts, and a
 * {@code root/job} id on job-change and skill quests, and a bare job root on level quests. Whether the ids exist in the game's data is
 * {@code QuestPathDataTest}'s job (it reads the job and recipe files).
 */
public final class QuestPathValidator {

    private QuestPathValidator() {
    }

    public static List<String> errors(QuestPath path) {
        List<String> errors = new ArrayList<>();
        Map<String, QuestPath.Batch> byId = new HashMap<>();
        for (QuestPath.Batch b : path.batches()) {
            if (byId.put(b.id(), b) != null) {
                errors.add(path.id() + ": duplicate batch id " + b.id());
            }
            if (b.quests().isEmpty()) {
                errors.add(path.batchTag(b) + ": no quests");
            }
            for (QuestPath.Step s : b.quests()) {
                errors.addAll(stepErrors(path.batchTag(b), s));
            }
        }
        if (path.batches().stream().noneMatch(QuestPath.Batch::isStarter)) {
            errors.add(path.id() + ": no starter batch (one with no opens_after)");
        }
        for (QuestPath.Batch b : path.batches()) {
            for (String after : b.opensAfter()) {
                if (!byId.containsKey(after)) {
                    errors.add(path.batchTag(b) + ": opens_after names unknown batch " + after);
                }
            }
        }
        for (QuestPath.Batch b : path.batches()) {
            if (reachesItself(b.id(), b.id(), byId, new HashSet<>())) {
                errors.add(path.batchTag(b) + ": opens_after cycle");
            }
        }
        return errors;
    }

    private static List<String> stepErrors(String where, QuestPath.Step s) {
        List<String> errors = new ArrayList<>();
        if (s.count() < 1) {
            errors.add(where + ": count must be at least 1 for " + s.id());
        }
        boolean needsJobId = s.kind() == QuestPath.Kind.JOB_CHANGE || s.kind() == QuestPath.Kind.SKILL;
        if (needsJobId && s.id().split("/").length != 2) {
            errors.add(where + ": " + s.kind() + " wants a root/job id, got " + s.id());
        }
        if (s.kind() == QuestPath.Kind.LEVEL && s.id().contains("/")) {
            errors.add(where + ": LEVEL wants a job root, got " + s.id());
        }
        return errors;
    }

    private static boolean reachesItself(String start, String at, Map<String, QuestPath.Batch> byId, Set<String> seen) {
        QuestPath.Batch b = byId.get(at);
        if (b == null || !seen.add(at)) {
            return false;
        }
        for (String after : b.opensAfter()) {
            if (after.equals(start) || reachesItself(start, after, byId, seen)) {
                return true;
            }
        }
        return false;
    }
}
