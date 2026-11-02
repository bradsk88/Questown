package ca.bradj.questown.town.quests.paths;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/** Every shipped path file parses, passes {@link QuestPathValidator}, and names jobs and rooms that exist. */
class QuestPathDataTest {

    private static final Path DATA = Path.of("src", "main", "resources", "data", "questown");
    private static final Gson GSON = new Gson();
    private static final List<String> ARMOR_MATERIALS = List.of("leather", "iron", "golden", "diamond");
    private static final List<String> ARMOR_PIECES = List.of("boots", "helmet", "leggings", "chestplate");

    @Test
    void everyPathFile_parsesValidatesAndNamesRealJobsAndRooms() throws IOException {
        Set<String> jobs = jobIds();
        List<String> problems = new ArrayList<>();
        List<Path> files = files(DATA.resolve("questown_paths"));
        Assertions.assertFalse(files.isEmpty(), "no path files");
        for (Path f : files) {
            QuestPath path = QuestPathParser.parse(read(f));
            problems.addAll(QuestPathValidator.errors(path));
            for (QuestPath.Batch b : path.batches()) {
                for (QuestPath.Step s : b.quests()) {
                    problems.addAll(stepProblems(path.batchTag(b), s, jobs));
                }
            }
        }
        Assertions.assertEquals(List.of(), problems);
    }

    @Test
    void outfitterShipsTheFirstCut() throws IOException {
        QuestPath p = QuestPathParser.parse(read(DATA.resolve("questown_paths").resolve("outfitter.json")));

        Assertions.assertEquals(List.of("start", "train_crafter_2", "train_crafter_3", "armor", "weapons_1", "train_crafter_5", "weapons_2", "showcase"),
                p.batches().stream().map(QuestPath.Batch::id).toList());
        Assertions.assertEquals(List.of(true, false, false, false, false, false, true, false),
                p.batches().stream().map(QuestPath.Batch::arrival).toList(), "plan ruling 19");
    }

    /** A batch that names a skill opens only after a level quest reaches the skill's level. */
    @Test
    void everyNamedSkill_isLearnableWhenItsBatchOpens() throws IOException {
        Map<String, Integer> gates = skillLevelGates();
        List<String> problems = new ArrayList<>();
        for (Path f : files(DATA.resolve("questown_paths"))) {
            QuestPath path = QuestPathParser.parse(read(f));
            Map<String, QuestPath.Batch> byId = new HashMap<>();
            path.batches().forEach(b -> byId.put(b.id(), b));
            for (QuestPath.Batch b : path.batches()) {
                for (QuestPath.Step s : b.quests()) {
                    if (s.kind() != QuestPath.Kind.SKILL) {
                        continue;
                    }
                    String root = s.id().split("/")[0];
                    int gate = gates.getOrDefault(s.id(), DEFAULT_LEVEL);
                    int trained = trainedLevel(root, b, byId, new HashSet<>());
                    if (gate > 1 && trained < gate) {
                        problems.add(path.batchTag(b) + ": " + s.id() + " needs level " + gate
                                + " but the batch opens after level " + trained);
                    }
                }
            }
        }
        Assertions.assertEquals(List.of(), problems);
    }

    /** That level quest asks for exactly the level the skills need. */
    @Test
    void everySkillBatch_waitsOnlyForTheLevelItNeeds() throws IOException {
        Map<String, Integer> gates = skillLevelGates();
        List<String> problems = new ArrayList<>();
        for (Path f : files(DATA.resolve("questown_paths"))) {
            QuestPath path = QuestPathParser.parse(read(f));
            Map<String, QuestPath.Batch> byId = new HashMap<>();
            path.batches().forEach(b -> byId.put(b.id(), b));
            for (QuestPath.Batch b : path.batches()) {
                Map<String, Integer> needed = new HashMap<>();
                for (QuestPath.Step s : b.quests()) {
                    if (s.kind() == QuestPath.Kind.SKILL) {
                        needed.merge(s.id().split("/")[0], gates.getOrDefault(s.id(), DEFAULT_LEVEL), Math::max);
                    }
                }
                needed.forEach((root, gate) -> {
                    int trained = directLevelGate(root, b, byId);
                    if (trained > gate) {
                        problems.add(path.batchTag(b) + ": skills need " + root + " level " + gate
                                + " but the batch waits for level " + trained);
                    }
                });
            }
        }
        Assertions.assertEquals(List.of(), problems);
    }

    private static int directLevelGate(String root, QuestPath.Batch b, Map<String, QuestPath.Batch> byId) {
        int best = 1;
        for (String after : b.opensAfter()) {
            QuestPath.Batch a = byId.get(after);
            if (a == null) {
                continue;
            }
            for (QuestPath.Step s : a.quests()) {
                if (s.kind() == QuestPath.Kind.LEVEL && s.id().equals(root)) {
                    best = Math.max(best, s.count());
                }
            }
        }
        return best;
    }

    private static int trainedLevel(String root, QuestPath.Batch b, Map<String, QuestPath.Batch> byId, Set<String> seen) {
        int best = 1;
        for (String after : b.opensAfter()) {
            QuestPath.Batch a = byId.get(after);
            if (a == null || !seen.add(after)) {
                continue;
            }
            for (QuestPath.Step s : a.quests()) {
                if (s.kind() == QuestPath.Kind.LEVEL && s.id().equals(root)) {
                    best = Math.max(best, s.count());
                }
            }
            best = Math.max(best, trainedLevel(root, a, byId, seen));
        }
        return best;
    }

    /** Work.DEFAULT_AVAILABLE_AT_LEVEL; Work can't load in a unit test. */
    private static final int DEFAULT_LEVEL = 2;

    private static Map<String, Integer> skillLevelGates() throws IOException {
        Map<String, Integer> gates = new HashMap<>();
        for (Path f : files(DATA.resolve("questown_jobs"))) {
            JsonObject job = read(f);
            if (job.has("id") && job.has("available_at_level")) {
                gates.put(job.get("id").getAsString(), job.get("available_at_level").getAsInt());
            }
        }
        for (String m : ARMOR_MATERIALS) {
            for (String p : ARMOR_PIECES) {
                gates.put("crafter/" + m + "_" + p, ca.bradj.questown.jobs.ArmorTierLevels.forPrefix(m));
            }
        }
        return gates;
    }

    private static List<String> stepProblems(String where, QuestPath.Step s, Set<String> jobs) {
        return switch (s.kind()) {
            case JOB_CHANGE, SKILL -> jobs.contains(s.id()) ? List.of() : List.of(where + ": no job " + s.id());
            case ROOM -> roomExists(s.id()) ? List.of() : List.of(where + ": no room recipe " + s.id());
            case LOOT -> s.id().contains(":") ? List.of() : List.of(where + ": loot wants a namespaced id " + s.id());
            case LEVEL -> jobs.stream().anyMatch(j -> j.startsWith(s.id() + "/"))
                    ? List.of() : List.of(where + ": no job root " + s.id());
        };
    }

    private static boolean roomExists(String id) {
        String[] parts = id.split(":");
        return parts.length == 2 && "questown".equals(parts[0])
                && Files.exists(DATA.resolve("recipes").resolve(parts[1] + ".json"));
    }

    private static Set<String> jobIds() throws IOException {
        Set<String> ids = new HashSet<>();
        for (Path f : files(DATA.resolve("questown_jobs"))) {
            JsonObject job = read(f);
            if (job.has("id")) {
                ids.add(job.get("id").getAsString());
            }
        }
        for (String m : ARMOR_MATERIALS) {
            for (String p : ARMOR_PIECES) {
                ids.add("crafter/" + m + "_" + p);
            }
        }
        return ids;
    }

    private static List<Path> files(Path dir) throws IOException {
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }
    }

    private static JsonObject read(Path f) throws IOException {
        try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
            return GSON.fromJson(r, JsonObject.class);
        }
    }
}
