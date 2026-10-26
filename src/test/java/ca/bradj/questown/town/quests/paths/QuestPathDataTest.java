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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Every shipped path file parses, passes {@link QuestPathValidator}, and names jobs and rooms that
 * exist in the data: job ids from {@code questown_jobs/*.json} plus the armorer template's
 * {@code crafter/<material>_<piece>} expansion (`ResourceJobLoader.makeArmorerJobs`), room ids from
 * {@code recipes/<path>.json}.
 */
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

        Assertions.assertEquals(List.of("start", "armor", "weapons_1", "weapons_2"),
                p.batches().stream().map(QuestPath.Batch::id).toList());
        Assertions.assertEquals(List.of(true, false, false, true),
                p.batches().stream().map(QuestPath.Batch::arrival).toList(), "plan ruling 19");
    }

    private static List<String> stepProblems(String where, QuestPath.Step s, Set<String> jobs) {
        return switch (s.kind()) {
            case JOB_CHANGE, SKILL -> jobs.contains(s.id()) ? List.of() : List.of(where + ": no job " + s.id());
            case ROOM -> roomExists(s.id()) ? List.of() : List.of(where + ": no room recipe " + s.id());
            case LOOT -> s.id().contains(":") ? List.of() : List.of(where + ": loot wants a namespaced id " + s.id());
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
