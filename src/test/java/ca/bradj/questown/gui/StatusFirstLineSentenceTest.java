package ca.bradj.questown.gui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Every status's first line is a sentence on its own, since the second can be replaced, except working states. */
class StatusFirstLineSentenceTest {

    private static final Path LANG = Path.of("src/main/resources/assets/questown/lang/en_us.json");

    private static final List<String> WORKING_PREFIXES = List.of(
            "tooltips.villagers.job.blacksmith.",
            "tooltips.villagers.job.crafter.",
            "tooltips.villagers.job.baker.",
            "tooltips.villagers.job.smelter.",
            "tooltips.villagers.job.one_mushroom_stew."
    );

    @Test
    void everyNonWorkingFirstLine_isASentence() throws IOException {
        JsonObject lang = JsonParser.parseString(Files.readString(LANG)).getAsJsonObject();
        Set<String> fragments = new TreeSet<>();
        for (String key : lang.keySet()) {
            if (!key.contains(".status_1.") || WORKING_PREFIXES.stream().anyMatch(key::startsWith)) {
                continue;
            }
            String text = lang.get(key).getAsString().strip();
            if (!text.matches(".*[.!?)]$")) {
                fragments.add(key + " = " + text);
            }
        }
        Assertions.assertEquals(Set.of(), fragments);
    }
}
