package ca.bradj.questown.logic;

import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RoomRecipesTest {

    @Test
    void containsAllTags_generalContainsSpecific() {
        ImmutableList<ImmutableList<String>> generalList = ImmutableList.of(
                ImmutableList.of("torch", "lantern")
        );
        ImmutableList<ImmutableList<String>> specificList = ImmutableList.of(
                ImmutableList.of("lantern")
        );
        Assertions.assertTrue(RoomRecipes.containsAllTags(generalList, specificList));
    }

    @Test
    void containsAllTags_specificDoesNotContainGeneral() {
        ImmutableList<ImmutableList<String>> generalList = ImmutableList.of(
                ImmutableList.of("torch", "lantern")
        );
        ImmutableList<ImmutableList<String>> specificList = ImmutableList.of(
                ImmutableList.of("lantern")
        );
        Assertions.assertFalse(RoomRecipes.containsAllTags(specificList, generalList));
    }

    @Test
    void containsAllTags_similarButNotSame() {
        ImmutableList<ImmutableList<String>> lightSources = ImmutableList.of(
                ImmutableList.of("torch", "lantern")
        );
        ImmutableList<ImmutableList<String>> heatSources = ImmutableList.of(
                ImmutableList.of("torch", "campfire")
        );
        Assertions.assertFalse(RoomRecipes.containsAllTags(heatSources, lightSources));
    }

    @Test
    void containsAllTags_unordered() {
        ImmutableList<ImmutableList<String>> lightSources = ImmutableList.of(
                ImmutableList.of("torch", "lantern")
        );
        ImmutableList<ImmutableList<String>> heatSources = ImmutableList.of(
                ImmutableList.of("lantern", "torch")
        );
        Assertions.assertTrue(RoomRecipes.containsAllTags(heatSources, lightSources));
    }

    @Test
    void containsAllTags_multipleExactSame() {
        ImmutableList<ImmutableList<String>> list = ImmutableList.of(
                ImmutableList.of("torch", "lantern"),
                ImmutableList.of("red_bed", "blue_bed")
        );
        Assertions.assertTrue(RoomRecipes.containsAllTags(list, list));
    }

    @Test
    void containsAllTags_multipleGeneralContainsSpecific() {
        ImmutableList<ImmutableList<String>> general = ImmutableList.of(
                ImmutableList.of("torch", "lantern"),
                ImmutableList.of("red_bed", "blue_bed")
        );
        ImmutableList<ImmutableList<String>> specific = ImmutableList.of(
                ImmutableList.of("lantern"),
                ImmutableList.of("red_bed")
        );
        Assertions.assertTrue(RoomRecipes.containsAllTags(general, specific));
    }

    @Test
    void containsAllTags_multipleGeneralContainsGeneralAndSpecific() {
        ImmutableList<ImmutableList<String>> general = ImmutableList.of(
                ImmutableList.of("torch", "lantern"),
                ImmutableList.of("red_bed", "blue_bed")
        );
        ImmutableList<ImmutableList<String>> specific = ImmutableList.of(
                ImmutableList.of("lantern"),
                ImmutableList.of("red_bed", "blue_bed")
        );
        Assertions.assertTrue(RoomRecipes.containsAllTags(general, specific));
    }

    @Test
    void containsAllTags_multipleGeneralContainsGeneralAndSpecific_Alt() {
        ImmutableList<ImmutableList<String>> general = ImmutableList.of(
                ImmutableList.of("torch", "lantern"),
                ImmutableList.of("red_bed", "blue_bed")
        );
        ImmutableList<ImmutableList<String>> specific = ImmutableList.of(
                ImmutableList.of("torch", "lantern"),
                ImmutableList.of("red_bed")
        );
        Assertions.assertTrue(RoomRecipes.containsAllTags(general, specific));
    }

    @Test
    void containsAllTags_multipleGeneralEntirelyDifferent() {
        ImmutableList<ImmutableList<String>> general = ImmutableList.of(
                ImmutableList.of("torch", "lantern"),
                ImmutableList.of("red_bed", "blue_bed")
        );
        ImmutableList<ImmutableList<String>> specific = ImmutableList.of(
                ImmutableList.of("jack_o_lantern", "campfire"),
                ImmutableList.of("green_bed", "yellow_bed")
        );
        Assertions.assertFalse(RoomRecipes.containsAllTags(general, specific));
    }
}