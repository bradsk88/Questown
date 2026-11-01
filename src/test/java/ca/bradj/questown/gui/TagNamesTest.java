package ca.bradj.questown.gui;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * A tag is named through the lang key {@code "#<tag>"} ({@link Ingredients#getName}), not shown as
 * its raw id; every tag the jobs and paths name gets one.
 */
class TagNamesTest {

    private static final Pattern TAG = Pattern.compile("\"(#[a-z0-9_.-]+:[a-z0-9_/.-]+)\"");

    @Test
    void everyTagInJobAndPathData_hasALangName() throws IOException {
        JsonObject lang = new Gson().fromJson(
                Files.readString(Path.of("src/main/resources/assets/questown/lang/en_us.json"), StandardCharsets.UTF_8),
                JsonObject.class
        );
        Set<String> missing = new TreeSet<>();
        for (String dir : List.of("questown_jobs", "questown_paths")) {
            try (Stream<Path> files = Files.walk(Path.of("src/main/resources/data/questown", dir))) {
                for (Path f : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                    Matcher m = TAG.matcher(Files.readString(f, StandardCharsets.UTF_8));
                    while (m.find()) {
                        if (!lang.has(m.group(1))) {
                            missing.add(m.group(1) + " (" + f.getFileName() + ")");
                        }
                    }
                }
            }
        }
        Assertions.assertEquals(Set.of(), missing);
    }
}
