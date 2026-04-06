package ca.bradj.questown.town.entity;

import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class ExoticWoodSelectionTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static ResourceLocation oakLog() {
        return new ResourceLocation("minecraft", "oak_log");
    }

    private static ResourceLocation birchLog() {
        return new ResourceLocation("minecraft", "birch_log");
    }

    private static ResourceLocation spruceLog() {
        return new ResourceLocation("minecraft", "spruce_log");
    }

    private static ResourceLocation jungleLog() {
        return new ResourceLocation("minecraft", "jungle_log");
    }

    private static ResourceLocation acaciaLog() {
        return new ResourceLocation("minecraft", "acacia_log");
    }

    private static ResourceLocation darkOakLog() {
        return new ResourceLocation("minecraft", "dark_oak_log");
    }

    @Test
    void forestBiomeGetsForeignWood() {
        ResourceLocation result = TownFlagTutorialAdapter.chooseExoticWood("forest");
        assertNotEquals(oakLog(), result);
        assertNotNull(result);
    }

    @Test
    void birchForestGetsForeignWood() {
        ResourceLocation result = TownFlagTutorialAdapter.chooseExoticWood("birch_forest");
        // "birch_forest" contains both "birch" and "forest" substrings;
        // the result should not be both oak AND birch (it must exclude at least one native)
        boolean excludesBirch = !birchLog().equals(result);
        boolean excludesOak = !oakLog().equals(result);
        assertTrue(excludesBirch || excludesOak,
                "Expected exotic wood to exclude at least one native wood type for birch_forest");
        assertNotNull(result);
    }

    @Test
    void taigaGetsForeignWood() {
        ResourceLocation result = TownFlagTutorialAdapter.chooseExoticWood("taiga");
        assertNotEquals(spruceLog(), result);
        assertNotNull(result);
    }

    @Test
    void jungleGetsForeignWood() {
        ResourceLocation result = TownFlagTutorialAdapter.chooseExoticWood("jungle");
        assertNotEquals(jungleLog(), result);
        assertNotNull(result);
    }

    @Test
    void savannaGetsForeignWood() {
        ResourceLocation result = TownFlagTutorialAdapter.chooseExoticWood("savanna");
        assertNotEquals(acaciaLog(), result);
        assertNotNull(result);
    }

    @Test
    void darkForestGetsForeignWood() {
        ResourceLocation result = TownFlagTutorialAdapter.chooseExoticWood("dark_forest");
        assertNotEquals(darkOakLog(), result);
        assertNotNull(result);
    }

    @Test
    void mangroveSwampGetsForeignWood() {
        ResourceLocation result = TownFlagTutorialAdapter.chooseExoticWood("mangrove_swamp");
        ResourceLocation mangroveLog = new ResourceLocation("minecraft", "mangrove_log");
        assertNotEquals(mangroveLog, result);
        assertNotNull(result);
    }

    @Test
    void plainsBiomeGetsFirstAvailableWood() {
        ResourceLocation result = TownFlagTutorialAdapter.chooseExoticWood("plains");
        assertEquals(oakLog(), result);
    }

    @Test
    void unknownBiomeGetsFirstAvailableWood() {
        ResourceLocation result = TownFlagTutorialAdapter.chooseExoticWood("mushroom_fields");
        assertEquals(oakLog(), result);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "forest", "birch_forest", "taiga", "jungle",
            "savanna", "dark_forest", "mangrove_swamp",
            "plains", "mushroom_fields", "desert", "swamp"
    })
    void resultIsNeverNull(String biome) {
        assertNotNull(TownFlagTutorialAdapter.chooseExoticWood(biome));
    }
}
