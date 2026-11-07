package ca.bradj.questown.jobs;

import ca.bradj.questown.loader.Loader;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.SharedConstants;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** Every job's {@code sound.id} is a registered sound; a missing one crashes the server when the job is worked. */
class JobSoundIdsDataTest {

    private static final Path JOBS_DIR = Path.of("src", "main", "resources", "data", "questown", "questown_jobs");

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void everyJobSound_isARegisteredSoundEvent() throws Exception {
        List<String> missing = new ArrayList<>();
        List<Path> files;
        try (Stream<Path> s = Files.list(JOBS_DIR)) {
            files = s.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }
        for (Path f : files) {
            JsonObject job;
            try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                job = new Gson().fromJson(r, JsonObject.class);
            }
            if (!job.has("sound") || !job.get("sound").isJsonObject()) {
                continue;
            }
            String id = job.getAsJsonObject("sound").get("id").getAsString();
            if (Loader.soundFromId(new ResourceLocation(id)) == null) {
                missing.add(f.getFileName() + ": " + id);
            }
        }
        Assertions.assertEquals(List.of(), missing);
    }
}
