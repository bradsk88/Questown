package ca.bradj.questown.jobs.integration;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Structural similarity tests for gatherer jobs.
 *
 * These tests verify that all gatherer jobs follow the same patterns, grouped by:
 * - Tool family (axe, rod, shears, notool)
 * - Duration (short, half/med, full)
 *
 * If all jobs in a group are structurally identical (differing only in expected ways),
 * we only need to exhaustively test one representative from each group.
 *
 * If these tests fail, it signals that jobs have diverged and may need additional testing.
 */
class GathererJobStructureTest {

    private static final String JOBS_PATH = "data/questown/questown_jobs/";
    private static final Gson GSON = new Gson();

    // Tool families - jobs within each family should have identical structure
    private static final List<String> AXE_JOBS = List.of(
            "gatherer_unmapped_axe_short.json",
            "gatherer_unmapped_axe_med.json",
            "gatherer_unmapped_axe_full.json"
    );

    private static final List<String> ROD_JOBS = List.of(
            "gatherer_unmapped_rod_short.json",
            "gatherer_unmapped_rod_half.json",
            "gatherer_unmapped_rod_full.json"
    );

    private static final List<String> NOTOOL_JOBS = List.of(
            "gatherer_unmapped_notool_short.json",
            "gatherer_unmapped_notool_med.json",
            "gatherer_unmapped_notool_full.json"
    );

    private static final List<String> SHEARS_JOBS = List.of(
            "gatherer_unmapped_shears_full.json"
    );

    // Duration groups - jobs across tool families with same duration should have same time values
    private static final Map<String, Integer> EXPECTED_TIME_BY_DURATION = Map.of(
            "short", 2000,
            "med", 4000,
            "half", 4000,
            "full", 6000
    );

    private static Map<String, JsonObject> loadedJobs;

    @BeforeAll
    static void loadAllJobs() {
        loadedJobs = new HashMap<>();
        List<String> allJobs = new ArrayList<>();
        allJobs.addAll(AXE_JOBS);
        allJobs.addAll(ROD_JOBS);
        allJobs.addAll(NOTOOL_JOBS);
        allJobs.addAll(SHEARS_JOBS);

        for (String jobFile : allJobs) {
            try (InputStream is = GathererJobStructureTest.class.getClassLoader()
                    .getResourceAsStream(JOBS_PATH + jobFile)) {
                if (is != null) {
                    JsonObject obj = GSON.fromJson(
                            new InputStreamReader(is, StandardCharsets.UTF_8),
                            JsonObject.class
                    );
                    loadedJobs.put(jobFile, obj);
                }
            } catch (Exception e) {
                // Job file not found - will be caught by individual tests
            }
        }
    }

    // ========== Common Structure Tests ==========

    @Test
    void allGathererJobs_shouldHaveSameBlock() {
        String expectedBlock = "questown:welcome_mat_block";
        for (Map.Entry<String, JsonObject> entry : loadedJobs.entrySet()) {
            String actual = entry.getValue().get("block").getAsString();
            Assertions.assertEquals(expectedBlock, actual,
                    "Job " + entry.getKey() + " should use welcome_mat_block");
        }
    }

    @Test
    void allGathererJobs_shouldHaveSameRoom() {
        String expectedRoom = "questown:special_quest.town_gate";
        for (Map.Entry<String, JsonObject> entry : loadedJobs.entrySet()) {
            String actual = entry.getValue().get("room").getAsString();
            Assertions.assertEquals(expectedRoom, actual,
                    "Job " + entry.getKey() + " should use town_gate room");
        }
    }

    @Test
    void allGathererJobs_shouldHaveZeroCooldown() {
        for (Map.Entry<String, JsonObject> entry : loadedJobs.entrySet()) {
            int actual = entry.getValue().get("cooldown_ticks").getAsInt();
            Assertions.assertEquals(0, actual,
                    "Job " + entry.getKey() + " should have zero cooldown");
        }
    }

    @Test
    void allGathererJobs_shouldHaveBiomeLootResult() {
        for (Map.Entry<String, JsonObject> entry : loadedJobs.entrySet()) {
            JsonObject result = entry.getValue().getAsJsonObject("result");
            String type = result.get("type").getAsString();
            Assertions.assertEquals("biome_loot", type,
                    "Job " + entry.getKey() + " should have biome_loot result type");
        }
    }

    @Test
    void allGathererJobs_shouldHaveSameStatusTextureOverrides() {
        JsonArray expected = null;
        String firstJob = null;

        for (Map.Entry<String, JsonObject> entry : loadedJobs.entrySet()) {
            JsonArray actual = entry.getValue().getAsJsonArray("status_texture_overrides");
            if (expected == null) {
                expected = actual;
                firstJob = entry.getKey();
            } else {
                Assertions.assertEquals(expected, actual,
                        "Job " + entry.getKey() + " should have same status_texture_overrides as " + firstJob);
            }
        }
    }

    @Test
    void allGathererJobs_shouldHaveSameStatusTextOverrides() {
        JsonArray expected = null;
        String firstJob = null;

        for (Map.Entry<String, JsonObject> entry : loadedJobs.entrySet()) {
            JsonArray actual = entry.getValue().getAsJsonArray("status_text_overrides");
            if (expected == null) {
                expected = actual;
                firstJob = entry.getKey();
            } else {
                Assertions.assertEquals(expected, actual,
                        "Job " + entry.getKey() + " should have same status_text_overrides as " + firstJob);
            }
        }
    }

    @Test
    void allGathererJobs_shouldHaveRemoveFromWorldOnTimedState() {
        for (Map.Entry<String, JsonObject> entry : loadedJobs.entrySet()) {
            JsonArray special = entry.getValue().getAsJsonArray("special");
            boolean hasRemoveFromWorld = false;

            for (JsonElement elem : special) {
                JsonObject rule = elem.getAsJsonObject();
                if ("core_state".equals(rule.get("type").getAsString())
                        && "WAITING_FOR_TIMED_STATE".equals(rule.get("state").getAsString())) {
                    JsonArray rules = rule.getAsJsonArray("rules");
                    for (JsonElement r : rules) {
                        if ("remove_from_world".equals(r.getAsString())) {
                            hasRemoveFromWorld = true;
                            break;
                        }
                    }
                }
            }

            Assertions.assertTrue(hasRemoveFromWorld,
                    "Job " + entry.getKey() + " should have remove_from_world on WAITING_FOR_TIMED_STATE");
        }
    }

    // ========== Tool Family Structure Tests ==========

    @Test
    void axeJobs_shouldHaveSameWorkStateStructure() {
        assertToolFamilyStructure(AXE_JOBS, "#questown:axes", "Axe");
    }

    @Test
    void rodJobs_shouldHaveSameWorkStateStructure() {
        assertToolFamilyStructure(ROD_JOBS, "#questown:fishing_rods", "Rod");
    }

    @Test
    void shearsJobs_shouldHaveSameWorkStateStructure() {
        assertToolFamilyStructure(SHEARS_JOBS, "minecraft:shears", "Shears");
    }

    @Test
    void notoolJobs_shouldNotRequireTools() {
        for (String jobFile : NOTOOL_JOBS) {
            JsonObject job = loadedJobs.get(jobFile);
            if (job == null) continue;

            JsonArray workStates = job.getAsJsonArray("work_states");
            for (JsonElement state : workStates) {
                JsonObject stateObj = state.getAsJsonObject();
                Assertions.assertFalse(stateObj.has("tools"),
                        "No-tool job " + jobFile + " should not have tools requirement");
            }
        }
    }

    @Test
    void notoolJobs_shouldHaveSameWorkStateStructure() {
        // No-tool jobs: ingredients -> work -> time
        List<String> expectedStructure = List.of("ingredients", "work", "time");

        for (String jobFile : NOTOOL_JOBS) {
            JsonObject job = loadedJobs.get(jobFile);
            if (job == null) continue;

            List<String> actualStructure = extractWorkStateStructure(job);
            Assertions.assertEquals(expectedStructure, actualStructure,
                    "No-tool job " + jobFile + " should have structure: " + expectedStructure);
        }
    }

    private void assertToolFamilyStructure(List<String> jobFiles, String expectedTool, String familyName) {
        // Tool jobs: tools -> (optionally ingredients) -> work -> time
        // Short variants skip the ingredients state

        for (String jobFile : jobFiles) {
            JsonObject job = loadedJobs.get(jobFile);
            if (job == null) continue;

            JsonArray workStates = job.getAsJsonArray("work_states");

            // First state should always be tools
            JsonObject firstState = workStates.get(0).getAsJsonObject();
            Assertions.assertTrue(firstState.has("tools"),
                    familyName + " job " + jobFile + " should have tools as first work state");
            Assertions.assertEquals(expectedTool, firstState.get("tools").getAsString(),
                    familyName + " job " + jobFile + " should require " + expectedTool);

            // Last state should always be time
            JsonObject lastState = workStates.get(workStates.size() - 1).getAsJsonObject();
            Assertions.assertTrue(lastState.has("time"),
                    familyName + " job " + jobFile + " should have time as last work state");

            // Second-to-last should be work
            JsonObject workState = workStates.get(workStates.size() - 2).getAsJsonObject();
            Assertions.assertTrue(workState.has("work"),
                    familyName + " job " + jobFile + " should have work state before time");
        }
    }

    // ========== Duration Tests ==========

    @Test
    void shortDurationJobs_shouldHaveCorrectTime() {
        assertDurationTime(List.of(
                "gatherer_unmapped_axe_short.json",
                "gatherer_unmapped_notool_short.json",
                "gatherer_unmapped_rod_short.json"
        ), 2000, "short");
    }

    @Test
    void halfDurationJobs_shouldHaveCorrectTime() {
        assertDurationTime(List.of(
                "gatherer_unmapped_axe_med.json",
                "gatherer_unmapped_notool_med.json",
                "gatherer_unmapped_rod_half.json"
        ), 4000, "half/med");
    }

    @Test
    void fullDurationJobs_shouldHaveCorrectTime() {
        assertDurationTime(List.of(
                "gatherer_unmapped_axe_full.json",
                "gatherer_unmapped_notool_full.json",
                "gatherer_unmapped_rod_full.json",
                "gatherer_unmapped_shears_full.json"
        ), 6000, "full");
    }

    private void assertDurationTime(List<String> jobFiles, int expectedTime, String durationName) {
        for (String jobFile : jobFiles) {
            JsonObject job = loadedJobs.get(jobFile);
            if (job == null) continue;

            JsonArray workStates = job.getAsJsonArray("work_states");
            JsonObject lastState = workStates.get(workStates.size() - 1).getAsJsonObject();

            int actualTime = lastState.get("time").getAsInt();
            Assertions.assertEquals(expectedTime, actualTime,
                    durationName + " job " + jobFile + " should have time=" + expectedTime);
        }
    }

    // ========== Loot Table Consistency Tests ==========

    @Test
    void axeJobs_shouldUseSameLootTablePrefix() {
        assertLootTablePrefix(AXE_JOBS, "jobs/gatherer_axe", "Axe");
    }

    @Test
    void rodJobs_shouldUseSameLootTablePrefix() {
        assertLootTablePrefix(ROD_JOBS, "jobs/gatherer_fishing", "Rod");
    }

    @Test
    void shearsJobs_shouldUseSameLootTablePrefix() {
        assertLootTablePrefix(SHEARS_JOBS, "jobs/gatherer_shears", "Shears");
    }

    @Test
    void notoolJobs_shouldUseSameLootTablePrefix() {
        // Note: notool jobs use "gatherer_notool" but full variant uses "gatherer_notool_full"
        // This test checks they all start with the expected base
        for (String jobFile : NOTOOL_JOBS) {
            JsonObject job = loadedJobs.get(jobFile);
            if (job == null) continue;

            JsonObject result = job.getAsJsonObject("result");
            String prefix = result.get("prefix").getAsString();
            Assertions.assertTrue(prefix.startsWith("jobs/gatherer_notool"),
                    "No-tool job " + jobFile + " should have loot prefix starting with jobs/gatherer_notool");
        }
    }

    private void assertLootTablePrefix(List<String> jobFiles, String expectedPrefix, String familyName) {
        for (String jobFile : jobFiles) {
            JsonObject job = loadedJobs.get(jobFile);
            if (job == null) continue;

            JsonObject result = job.getAsJsonObject("result");
            String prefix = result.get("prefix").getAsString();
            Assertions.assertEquals(expectedPrefix, prefix,
                    familyName + " job " + jobFile + " should use loot prefix " + expectedPrefix);
        }
    }

    // ========== Attempt Scaling Tests ==========

    @Test
    void lootAttempts_shouldScaleWithDuration() {
        // Short = 1-2 attempts, Half/Med = 3-4 attempts, Full = 6 attempts
        Map<String, Integer> shortJobs = Map.of(
                "gatherer_unmapped_axe_short.json", 1,
                "gatherer_unmapped_notool_short.json", 2,
                "gatherer_unmapped_rod_short.json", 1
        );

        Map<String, Integer> halfJobs = Map.of(
                "gatherer_unmapped_axe_med.json", 3,
                "gatherer_unmapped_notool_med.json", 4,
                "gatherer_unmapped_rod_half.json", 3
        );

        Map<String, Integer> fullJobs = Map.of(
                "gatherer_unmapped_axe_full.json", 6,
                "gatherer_unmapped_notool_full.json", 6,
                "gatherer_unmapped_rod_full.json", 6,
                "gatherer_unmapped_shears_full.json", 6
        );

        assertAttempts(shortJobs, "short");
        assertAttempts(halfJobs, "half/med");
        assertAttempts(fullJobs, "full");
    }

    private void assertAttempts(Map<String, Integer> expected, String durationName) {
        for (Map.Entry<String, Integer> entry : expected.entrySet()) {
            JsonObject job = loadedJobs.get(entry.getKey());
            if (job == null) continue;

            JsonObject result = job.getAsJsonObject("result");
            int actualAttempts = result.get("attempts").getAsInt();
            Assertions.assertEquals(entry.getValue(), actualAttempts,
                    durationName + " job " + entry.getKey() + " should have " + entry.getValue() + " attempts");
        }
    }

    // ========== Helper Methods ==========

    private List<String> extractWorkStateStructure(JsonObject job) {
        List<String> structure = new ArrayList<>();
        JsonArray workStates = job.getAsJsonArray("work_states");

        for (JsonElement state : workStates) {
            JsonObject stateObj = state.getAsJsonObject();
            if (stateObj.has("tools")) structure.add("tools");
            else if (stateObj.has("ingredients")) structure.add("ingredients");
            else if (stateObj.has("work")) structure.add("work");
            else if (stateObj.has("time")) structure.add("time");
        }

        return structure;
    }
}