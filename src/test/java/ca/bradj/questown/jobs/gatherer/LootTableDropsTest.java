package ca.bradj.questown.jobs.gatherer;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
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
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LootTableDropsTest {

    @Test
    void collectsItemEntriesFromNestedPoolsAndAlternatives() {
        JsonElement table = JsonParser.parseString("""
                {"pools": [
                  {"entries": [
                    {"type": "minecraft:item", "name": "minecraft:apple"},
                    {"type": "minecraft:alternatives", "children": [
                      {"type": "minecraft:item", "name": "minecraft:stick"},
                      {"type": "minecraft:empty"}
                    ]},
                    {"type": "minecraft:tag", "name": "minecraft:logs"}
                  ]}
                ]}
                """);

        assertEquals(Set.of("minecraft:apple", "minecraft:stick"), LootTableDrops.itemIds(List.of(table)));
    }

    @Test
    void theRealTablesPutWheatSeedsOnlyInTheNoToolGatherers() throws IOException, URISyntaxException {
        // The 2026-10-22 report: the board claimed hunters, miners and tool gatherers make seeds.
        assertTrue(drops("gatherer_notool").contains("minecraft:wheat_seeds"));
        assertTrue(drops("gatherer_notool_full").contains("minecraft:wheat_seeds"));
        for (String prefix : List.of("hunter_sword", "miner", "gatherer_axe", "gatherer_shears")) {
            assertFalse(drops(prefix).contains("minecraft:wheat_seeds"), prefix);
        }
    }

    private static Set<String> drops(String prefixDir) throws IOException, URISyntaxException {
        URL dir = LootTableDropsTest.class.getClassLoader()
                .getResource("data/questown/loot_tables/jobs/" + prefixDir);
        assertNotNull(dir, prefixDir);
        List<JsonElement> tables = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Path.of(dir.toURI()))) {
            for (Path p : files.filter(f -> f.toString().endsWith(".json")).toList()) {
                try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                    tables.add(JsonParser.parseReader(r));
                }
            }
        }
        return LootTableDrops.itemIds(tables);
    }
}
