package ca.bradj.questown.jobs.gatherer;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** A skill's "Produces" lists the likeliest items first. */
class LootTableLikelihoodTest {

    @Test
    void heavierEntries_comeFirst() {
        JsonElement table = JsonParser.parseString("""
                {"pools": [{"rolls": 1, "entries": [
                  {"type": "minecraft:item", "name": "minecraft:apple", "weight": 1},
                  {"type": "minecraft:item", "name": "minecraft:stick", "weight": 5},
                  {"type": "minecraft:item", "name": "minecraft:dirt", "weight": 3}
                ]}]}
                """);
        Assertions.assertEquals(
                ImmutableList.of("minecraft:stick", "minecraft:dirt", "minecraft:apple"),
                LootTableLikelihood.itemIdsByLikelihood(table)
        );
    }

    @Test
    void anItemInTwoPools_addsItsChances() {
        JsonElement table = JsonParser.parseString("""
                {"pools": [
                  {"rolls": 1, "entries": [
                    {"type": "minecraft:item", "name": "minecraft:stick"},
                    {"type": "minecraft:item", "name": "minecraft:apple"}
                  ]},
                  {"rolls": 1, "entries": [
                    {"type": "minecraft:item", "name": "minecraft:dirt"},
                    {"type": "minecraft:item", "name": "minecraft:apple"}
                  ]}
                ]}
                """);
        Assertions.assertEquals("minecraft:apple", LootTableLikelihood.itemIdsByLikelihood(table).get(0));
    }

    @Test
    void emptyEntries_takeTheirShareButAreNotListed() {
        JsonElement table = JsonParser.parseString("""
                {"pools": [{"rolls": 1, "entries": [
                  {"type": "minecraft:empty", "weight": 10},
                  {"type": "minecraft:item", "name": "minecraft:apple", "weight": 1}
                ]}]}
                """);
        Assertions.assertEquals(ImmutableList.of("minecraft:apple"), LootTableLikelihood.itemIdsByLikelihood(table));
    }

    @Test
    void theHuntersDefaultTable_leadsWithLeatherMuttonPorkchop() throws IOException {
        String path = "data/questown/loot_tables/jobs/hunter_sword/default.json";
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(path)) {
            Assertions.assertNotNull(in, path);
            JsonElement table = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            Assertions.assertEquals(
                    ImmutableList.of("minecraft:leather", "minecraft:mutton", "minecraft:porkchop"),
                    LootTableLikelihood.itemIdsByLikelihood(table).subList(0, 3)
            );
        }
    }
}
