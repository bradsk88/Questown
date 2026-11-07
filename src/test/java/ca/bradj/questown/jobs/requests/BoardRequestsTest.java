package ca.bradj.questown.jobs.requests;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/** A board request pauses once storage holds {@link BoardRequests#PAUSE_AT}. */
class BoardRequestsTest {

    @BeforeAll
    static void boot() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void stocked_pausesOnlyRequestsStorageHoldsEnoughOf() {
        Map<String, Integer> storage = Map.of("stick", BoardRequests.PAUSE_AT, "sword", BoardRequests.PAUSE_AT - 1);
        ImmutableSet<String> paused = BoardRequests.stocked(
                List.of("stick", "sword", "boots"),
                r -> storage.getOrDefault(r, 0),
                r -> false
        );
        Assertions.assertEquals(ImmutableSet.of("stick"), paused);
    }

    @Test
    void stocked_neverPausesARequestAnOpenQuestNeeds() {
        ImmutableSet<String> paused = BoardRequests.stocked(
                List.of("stick", "leather"),
                r -> 64,
                "leather"::equals
        );
        Assertions.assertEquals(ImmutableSet.of("stick"), paused);
    }

    @Test
    void allPaused_boardIsNotEmptyButOffersNoWork() {
        WorkRequest stick = WorkRequest.of(Items.STICK);
        BoardRequests board = new BoardRequests(ImmutableList.of(stick), ImmutableSet.of(stick));
        // Still not empty: an empty board lets villagers pick any work.
        Assertions.assertFalse(board.isEmpty());
        Assertions.assertEquals(ImmutableList.of(), board.toWork());
    }

    @Test
    void toWork_keepsUnpausedRequestsInOrder() {
        WorkRequest stick = WorkRequest.of(Items.STICK);
        WorkRequest sword = WorkRequest.of(Items.WOODEN_SWORD);
        WorkRequest boots = WorkRequest.of(Items.LEATHER_BOOTS);
        BoardRequests board = new BoardRequests(ImmutableList.of(stick, sword, boots), ImmutableSet.of(sword));
        Assertions.assertEquals(ImmutableList.of(stick, boots), board.toWork());
    }

    @Test
    void toWorkFor_aJobThatMakesNothingStillSeesPausedRequests() {
        WorkRequest armor = WorkRequest.of(Items.LEATHER_BOOTS);
        WorkRequest stick = WorkRequest.of(Items.STICK);
        BoardRequests board = new BoardRequests(ImmutableList.of(armor, stick), ImmutableSet.of(armor));
        Assertions.assertEquals(ImmutableList.of(armor, stick), board.toWorkFor(true));
        Assertions.assertEquals(ImmutableList.of(stick), board.toWorkFor(false));
    }
}
