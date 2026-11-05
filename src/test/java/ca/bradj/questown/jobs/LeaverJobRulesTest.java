package ca.bradj.questown.jobs;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

class LeaverJobRulesTest {

    private static final Path JOBS = Path.of("src/main/resources/data/questown/questown_jobs");
    private static final String WELCOME_MAT = "questown:welcome_mat_block";

    @Test
    void everyJobAtTheWelcomeMat_seedsIt_soItsVillagersCanStartAtState0() throws IOException {
        // For example: a gatherer whose mat had no work state would never take its food to the
        // mat, and would stand in the town collecting supplies forever.
        List<String> missing = new ArrayList<>();
        for (Path p : jobFiles()) {
            JsonObject job = read(p);
            if (worksAtTheWelcomeMat(job) && !globalRules(job).contains(SpecialRules.SEED_JOB_BLOCK)) {
                missing.add(p.getFileName().toString());
            }
        }
        Assertions.assertEquals(List.of(), missing);
    }

    @Test
    void leaverJobs_logTheirStatusReasons() throws IOException {
        // For example: a playtest that sees a hunter stuck "collecting supplies" reads the [WHY]
        // lines to find out which check failed.
        List<String> missing = new ArrayList<>();
        for (Path p : jobFiles()) {
            String name = p.getFileName().toString();
            if (name.matches("(gatherer|hunter|miner)_.*") && !globalRules(read(p)).contains(SpecialRules.LOG_STATUS_REASONS)) {
                missing.add(name);
            }
        }
        Assertions.assertEquals(List.of(), missing);
    }

    private static boolean worksAtTheWelcomeMat(JsonObject job) {
        JsonElement block = job.get("block");
        return block != null && block.isJsonPrimitive() && WELCOME_MAT.equals(block.getAsString());
    }

    private static List<Path> jobFiles() throws IOException {
        try (Stream<Path> s = Files.list(JOBS)) {
            return s.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }
    }

    private static JsonObject read(Path p) throws IOException {
        return JsonParser.parseString(Files.readString(p)).getAsJsonObject();
    }

    private static List<String> globalRules(JsonObject job) {
        List<String> out = new ArrayList<>();
        if (!job.has("special")) {
            return out;
        }
        for (JsonElement row : job.getAsJsonArray("special")) {
            JsonObject r = row.getAsJsonObject();
            if ("global".equals(r.get("type").getAsString())) {
                r.getAsJsonArray("rules").forEach(e -> out.add(e.getAsString()));
            }
        }
        return out;
    }
}
