package ca.bradj.questown.commands.test;

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
import java.util.List;
import java.util.stream.Stream;

/**
 * 2026-10-29: {@code hunter/sword [one_day_yield]} failed 1 in 3 runs with 10 items against a cap
 * of 8. The cap came from the trip yields before Outfitter ruling #25 (2026-10-26) raised half-day
 * trips to 5 attempts and full-day trips to 6; two half-day trips bank 10 in 8000 ticks.
 * 2026-10-30 (dev: hunters should bring more meat, with the longer trips still worth it): hunter
 * meat is 2 per roll and the full-day trip has 8 attempts, so the hunter cap also scales by the
 * largest per-roll count in its loot tables.
 */
class OneDayYieldCapsTest {

    private static final Path JOBS = Path.of("src/main/resources/data/questown/questown_jobs");
    private static final Gson GSON = new Gson();

    @Test
    void hunterCap_isTheBestTierAcrossOneDay() throws IOException {
        Assertions.assertEquals(maxCountPerRoll(Path.of("src/main/resources/data/questown/loot_tables/jobs/hunter_sword")),
                OneDayYieldCaps.HUNTER_ITEMS_PER_ROLL);
        Assertions.assertEquals(bestDayYield("hunter_unmapped_") * OneDayYieldCaps.HUNTER_ITEMS_PER_ROLL, OneDayYieldCaps.HUNTER);
    }

    /** The largest set_count in any table under dir (1 for an entry without one). */
    private static int maxCountPerRoll(Path dir) throws IOException {
        int max = 1;
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".json")).toList()) {
                JsonObject table = GSON.fromJson(Files.readString(p), JsonObject.class);
                for (JsonElement pool : table.getAsJsonArray("pools")) {
                    for (JsonElement entry : pool.getAsJsonObject().getAsJsonArray("entries")) {
                        JsonObject e = entry.getAsJsonObject();
                        if (!e.has("functions")) {
                            continue;
                        }
                        for (JsonElement fn : e.getAsJsonArray("functions")) {
                            JsonObject f = fn.getAsJsonObject();
                            if ("minecraft:set_count".equals(f.get("function").getAsString()) && f.get("count").isJsonPrimitive()) {
                                max = Math.max(max, f.get("count").getAsInt());
                            }
                        }
                    }
                }
            }
        }
        return max;
    }

    @Test
    void gathererCap_isTheBestTierAcrossOneDay() throws IOException {
        Assertions.assertEquals(bestDayYield("gatherer_unmapped_axe_"), OneDayYieldCaps.GATHERER);
    }

    private static int bestDayYield(String filePrefix) throws IOException {
        int best = 0;
        for (JsonObject job : jobs(filePrefix)) {
            int attempts = job.getAsJsonObject("result").get("attempts").getAsInt();
            best = Math.max(best, OneDayYieldCaps.capFor(attempts, tripTicks(job)));
        }
        Assertions.assertTrue(best > 0, "no trip tiers found for " + filePrefix);
        return best;
    }

    private static int tripTicks(JsonObject job) {
        int ticks = 0;
        for (JsonElement state : job.getAsJsonArray("work_states")) {
            JsonObject s = state.getAsJsonObject();
            if (s.has("time")) {
                ticks += s.get("time").getAsInt();
            }
        }
        return ticks;
    }

    private static List<JsonObject> jobs(String filePrefix) throws IOException {
        try (Stream<Path> files = Files.list(JOBS)) {
            List<Path> matching = files.filter(p -> p.getFileName().toString().startsWith(filePrefix)).toList();
            List<JsonObject> out = new java.util.ArrayList<>();
            for (Path p : matching) {
                try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                    out.add(GSON.fromJson(r, JsonObject.class));
                }
            }
            return out;
        }
    }
}
