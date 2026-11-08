package ca.bradj.questown.jobs;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/** A Block of Progress rolls among jobs with no parent; one root with two such jobs would double its odds. */
class JobDataRootsTest {

    private static final Gson GSON = new Gson();

    @Test
    void eachRootIdHasExactlyOneParentlessJob() throws IOException, URISyntaxException {
        Map<String, List<String>> parentlessByRoot = new TreeMap<>();
        for (JsonObject job : allJobs()) {
            JsonElement parent = job.get("parent");
            if (parent != null && !parent.isJsonNull()) {
                continue;
            }
            String id = job.get("id").getAsString();
            String root = id.split("/")[0];
            parentlessByRoot.computeIfAbsent(root, k -> new ArrayList<>()).add(id);
        }
        parentlessByRoot.values().removeIf(ids -> ids.size() == 1);
        Assertions.assertEquals(Map.of(), parentlessByRoot, "roots with more than one parentless job");
    }

    /** Every crafter job names its own working art. */
    @Test
    void everyCrafterJobDeclaresItsWorkingArt() throws IOException, URISyntaxException {
        List<String> missing = new ArrayList<>();
        for (JsonObject job : allJobs()) {
            String id = job.get("id").getAsString();
            if (id.startsWith("crafter/") && !hasWorkingTexture(job)) {
                missing.add(id);
            }
        }
        Assertions.assertEquals(List.of(), missing, "crafter jobs without a \"working\" status texture");
    }

    @Test
    void blacksmithsTableJobs_showTheBlacksmithsTable() throws IOException, URISyntaxException {
        Assertions.assertEquals(List.of(), jobsAtBlockWithoutArt("questown:blacksmiths_table",
                "questown:textures/blocks/blacksmiths_table_front.png"), "blacksmith's table jobs without its working art");
    }

    @Test
    void armorersTableJobs_showTheArmorersTable() throws IOException, URISyntaxException {
        Assertions.assertEquals(List.of(), jobsAtBlockWithoutArt("questown:armorers_table",
                "questown:textures/blocks/armorers_table_front.png"), "armorer's table jobs without its working art");
    }

    private static List<String> jobsAtBlockWithoutArt(String blockId, String texture) throws IOException, URISyntaxException {
        List<String> wrong = new ArrayList<>();
        for (JsonObject job : allJobs()) {
            JsonElement block = job.get("block");
            boolean atBlock = block != null && block.isJsonPrimitive() && blockId.equals(block.getAsString());
            if (atBlock && !texture.equals(workingTexture(job))) {
                wrong.add(job.get("id").getAsString());
            }
        }
        return wrong;
    }

    private static boolean hasWorkingTexture(JsonObject job) {
        return workingTexture(job) != null;
    }

    private static String workingTexture(JsonObject job) {
        if (!job.has("status_texture_overrides")) {
            return null;
        }
        for (JsonElement row : job.getAsJsonArray("status_texture_overrides")) {
            JsonObject r = row.getAsJsonObject();
            if ("working".equals(r.get("type").getAsString())) {
                return r.get("new_texture").getAsString();
            }
        }
        return null;
    }

    private static List<JsonObject> allJobs() throws IOException, URISyntaxException {
        URL dir = JobDataRootsTest.class.getClassLoader().getResource("data/questown/questown_jobs");
        Assertions.assertNotNull(dir, "job data directory on the test classpath");
        List<JsonObject> jobs = new ArrayList<>();
        try (Stream<Path> files = Files.list(Path.of(dir.toURI()))) {
            for (Path p : files.filter(f -> f.toString().endsWith(".json")).toList()) {
                try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                    JsonObject job = GSON.fromJson(r, JsonObject.class);
                    if (job.has("id")) {
                        jobs.add(job);
                    }
                }
            }
        }
        Assertions.assertFalse(jobs.isEmpty(), "job data files found");
        return jobs;
    }
}
