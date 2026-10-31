package ca.bradj.questown.jobs;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * A job that takes its product out of a block slot ({@code take_from_slot_N}, e.g. cook/extract,
 * baker/extract) produces whatever that slot holds, so its {@code initial_request} must be the
 * slot's item: the work picker only takes a job whose request-or-result satisfies a board
 * request. 2026-10-20 playtest: cook/extract requested {@code minecraft:cooked_cod} while its slot
 * held {@code #questown:villager_food}, so with a cooked-mutton request on the board the cook
 * never extracted ("[Product not requested]") and the furnace output piled up.
 */
class SlotExtractJobRequestTest {

    private static final Path JOBS = Path.of("src/main/resources/data/questown/questown_jobs");
    private static final Gson GSON = new Gson();

    @Test
    void slotExtractJobs_requestWhatTheirSlotHolds() throws IOException {
        List<String> mismatches = new ArrayList<>();
        int checked = 0;
        try (Stream<Path> files = Files.list(JOBS)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                JsonObject job = read(f);
                Integer slot = takeFromSlot(job);
                if (slot == null) {
                    continue;
                }
                String slotItem = slotItem(job, slot);
                if (slotItem == null) {
                    continue;
                }
                checked++;
                String request = job.has("initial_request") ? job.get("initial_request").getAsString() : null;
                if (!slotItem.equals(request)) {
                    mismatches.add(f.getFileName() + ": slot " + slot + " holds " + slotItem + ", initial_request " + request);
                }
            }
        }
        Assertions.assertTrue(checked >= 2, "expected cook/extract and baker/extract, checked " + checked);
        Assertions.assertEquals(List.of(), mismatches);
    }

    private static Integer takeFromSlot(JsonObject job) {
        if (!job.has("special")) {
            return null;
        }
        for (JsonElement s : job.getAsJsonArray("special")) {
            for (JsonElement r : s.getAsJsonObject().getAsJsonArray("rules")) {
                String rule = r.getAsString();
                int i = rule.indexOf("take_from_slot_");
                if (i >= 0) {
                    return Integer.parseInt(rule.substring(i + "take_from_slot_".length()));
                }
            }
        }
        return null;
    }

    private static String slotItem(JsonObject job, int slot) {
        if (!job.has("block") || !job.getAsJsonObject("block").has("has_item_in_slot_initially")) {
            return null;
        }
        String spec = job.getAsJsonObject("block").get("has_item_in_slot_initially").getAsString();
        String[] parts = spec.split("/", 2);
        return Integer.parseInt(parts[0]) == slot ? parts[1] : null;
    }

    private static JsonObject read(Path f) throws IOException {
        try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
            return GSON.fromJson(r, JsonObject.class);
        }
    }
}
