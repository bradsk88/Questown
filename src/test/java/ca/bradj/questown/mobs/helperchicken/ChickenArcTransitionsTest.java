package ca.bradj.questown.mobs.helperchicken;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** {@link ChickenArcTransitions}: a lesson met early completes once the lessons before it do, and the optional free-food lesson is skipped when the next one is met. */
class ChickenArcTransitionsTest {

    private static ChickenArcTransitions.Observed allFalse() {
        return new ChickenArcTransitions.Observed(
                false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false);
    }

    private static ChickenArcTransitions.Observed allTrue() {
        return new ChickenArcTransitions.Observed(
                true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true);
    }

    @Test
    void stick_doesNotAdvance_whenNoWand() {
        ChickenBeatState next = ChickenArcTransitions.advance(
                ChickenBeatState.WAITING_FOR_STICK, allFalse()
        );
        Assertions.assertEquals(ChickenBeatState.WAITING_FOR_STICK, next);
    }

    @Test
    void stick_advancesToWallBlock_whenPlayerHasWand() {
        ChickenArcTransitions.Observed obs = new ChickenArcTransitions.Observed(
                true, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false);
        ChickenBeatState next = ChickenArcTransitions.advance(
                ChickenBeatState.WAITING_FOR_STICK, obs
        );
        Assertions.assertEquals(ChickenBeatState.WAITING_FOR_WALL_BLOCK, next);
    }

    @Test
    void campfire_advancesToPressurePlate_whenLit() {
        // A lit campfire moves straight to the welcome mat lesson.
        ChickenArcTransitions.Observed obs = new ChickenArcTransitions.Observed(
                true, true, false, false, false, false, false, false, false, false, false, false, false, false, false, false);
        ChickenBeatState next = ChickenArcTransitions.advance(
                ChickenBeatState.WAITING_FOR_WAND_ON_CAMPFIRE, obs
        );
        Assertions.assertEquals(ChickenBeatState.WAITING_FOR_PRESSURE_PLATE, next);
    }

    @Test
    void sunsetAndMap_advancesToSignOnSleep() {
        ChickenArcTransitions.Observed obs = new ChickenArcTransitions.Observed(
                true, true, true, false, false, false, false, false, false, false, false, false, false, false, false, false);
        ChickenBeatState next = ChickenArcTransitions.advance(
                ChickenBeatState.SUNSET_AND_MAP, obs
        );
        Assertions.assertEquals(ChickenBeatState.WAITING_FOR_SIGN, next);
    }

    @Test
    void stick_stopsAtWallBlock_whenWandAndCampfireMetButNoWall() {
        // Wand and campfire are done, but the wall lesson isn't, so it stops there.
        ChickenArcTransitions.Observed obs = new ChickenArcTransitions.Observed(
                true, true, false, false, false, false, false, false, false, false, false, false, false, false, false, false);
        ChickenBeatState next = ChickenArcTransitions.advance(
                ChickenBeatState.WAITING_FOR_STICK, obs
        );
        Assertions.assertEquals(ChickenBeatState.WAITING_FOR_WALL_BLOCK, next);
    }

    @Test
    void outOfOrder_chestAndCampfireBeforeDoor_cascadesToPressurePlate() {
        // Chest and campfire done early: once the room exists it moves through both to the mat.
        ChickenArcTransitions.Observed obs = new ChickenArcTransitions.Observed(
                true, true, false, true, true, true, true, false, true, false, false, false, false, false, false, false);
        ChickenBeatState next = ChickenArcTransitions.advance(
                ChickenBeatState.WAITING_FOR_ROOM_BUILD, obs
        );
        Assertions.assertEquals(ChickenBeatState.WAITING_FOR_PRESSURE_PLATE, next);
    }

    @Test
    void awaitingSeedsDelivery_advancesToComplete_whenSeedsGiven() {
        ChickenArcTransitions.Observed obs = allTrue();
        ChickenBeatState next = ChickenArcTransitions.advance(
                ChickenBeatState.AWAITING_WORLDLY_SEEDS_DELIVERY, obs
        );
        Assertions.assertEquals(ChickenBeatState.COMPLETE, next);
    }

    @Test
    void roomBuild_stopsAtRoomBuild_whenNoRoomDetected() {
        // A registered door with no room stops at the room-build lesson.
        ChickenArcTransitions.Observed obs = new ChickenArcTransitions.Observed(
                true, true, true, true, true, true, false, true, true,
                false, false, false, false, false, false, false
        );
        ChickenBeatState next = ChickenArcTransitions.advance(
                ChickenBeatState.WAITING_FOR_ROOM_BUILD, obs
        );
        Assertions.assertEquals(ChickenBeatState.WAITING_FOR_ROOM_BUILD, next);
    }

    @Test
    void roomBuild_advancesToChest_whenRoomDetected() {
        ChickenArcTransitions.Observed obs = new ChickenArcTransitions.Observed(
                true, true, true, true, true, true, true, true, true,
                false, false, false, false, false, false, false
        );
        ChickenBeatState next = ChickenArcTransitions.advance(
                ChickenBeatState.WAITING_FOR_ROOM_BUILD, obs
        );
        Assertions.assertEquals(ChickenBeatState.WAITING_FOR_PRESSURE_PLATE, next);
    }

    @Test
    void roomBuild_stopsWandOnDoor_whenRegisteredButNoRoom() {
        // Stops at the room-build lesson even with the chest placed.
        ChickenArcTransitions.Observed obs = new ChickenArcTransitions.Observed(
                true, true, true, true, true, true, false, true, true,
                false, false, false, false, false, false, false
        );
        ChickenBeatState next = ChickenArcTransitions.advance(
                ChickenBeatState.WAITING_FOR_WAND_ON_DOOR, obs
        );
        Assertions.assertEquals(ChickenBeatState.WAITING_FOR_ROOM_BUILD, next);
    }

    @Test
    void complete_isTerminal() {
        ChickenArcTransitions.Observed obs = allTrue();
        ChickenBeatState next = ChickenArcTransitions.advance(
                ChickenBeatState.COMPLETE, obs
        );
        Assertions.assertEquals(ChickenBeatState.COMPLETE, next);
    }

    @Test
    void forfeit_isTerminal() {
        ChickenArcTransitions.Observed obs = allTrue();
        ChickenBeatState next = ChickenArcTransitions.advance(
                ChickenBeatState.FORFEIT, obs
        );
        Assertions.assertEquals(ChickenBeatState.FORFEIT, next);
    }

    @Test
    void advanceNeverJumpsIntoForfeit() {
        // With everything true it ends at COMPLETE, never FORFEIT.
        ChickenArcTransitions.Observed obs = allTrue();
        ChickenBeatState next = ChickenArcTransitions.advance(
                ChickenBeatState.WAITING_FOR_STICK, obs
        );
        Assertions.assertEquals(ChickenBeatState.COMPLETE, next);
    }

    @Test
    void idempotent_sameInputs_sameOutput() {
        ChickenArcTransitions.Observed obs = new ChickenArcTransitions.Observed(
                true, true, true, false, false, false, false, false, false, false, false, false, false, false, false, false);
        ChickenBeatState once = ChickenArcTransitions.advance(
                ChickenBeatState.WAITING_FOR_STICK, obs
        );
        ChickenBeatState twice = ChickenArcTransitions.advance(once, obs);
        Assertions.assertEquals(once, twice);
    }

    @Test
    void freeFood_beat_stalls_whenBonusNotGivenAndNextNotMet() {
        // The free-food lesson waits while neither it nor the next is met.
        ChickenArcTransitions.Observed obs = allFalse();
        ChickenBeatState next = ChickenArcTransitions.advance(
                ChickenBeatState.WAITING_FOR_FREE_FOOD, obs
        );
        Assertions.assertEquals(ChickenBeatState.WAITING_FOR_FREE_FOOD, next);
    }

    @Test
    void freeFood_beat_skips_whenNextBeatMet() {
        // The villager menu opened without the free food: the free-food lesson is skipped.
        ChickenArcTransitions.Observed obs = new ChickenArcTransitions.Observed(
                false, false, false, false, false, false, false, false, false, false, false, true, false, false, false, false);
        ChickenBeatState next = ChickenArcTransitions.advance(
                ChickenBeatState.WAITING_FOR_FREE_FOOD, obs
        );
        Assertions.assertEquals(ChickenBeatState.AWAITING_WORLDLY_SEEDS_DELIVERY, next);
    }

    @Test
    void freeFood_beat_completes_whenBonusGiven() {
        ChickenArcTransitions.Observed obs = new ChickenArcTransitions.Observed(
                false, false, false, false, false, false, false, false, false, false, true, false, false, false, false, false);
        ChickenBeatState next = ChickenArcTransitions.advance(
                ChickenBeatState.WAITING_FOR_FREE_FOOD, obs
        );
        Assertions.assertEquals(ChickenBeatState.WAITING_FOR_VILLAGER_UI, next);
    }
}
