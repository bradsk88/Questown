package ca.bradj.questown.mobs.helperchicken;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Covers the pure state-machine function in {@link ChickenArcTransitions}.
 *
 * <p>The observed-conditions snapshot is a pure record. These tests construct
 * snapshots directly and verify the advance function returns the expected
 * next state — including the out-of-order acceptance behaviour (R7b) where
 * later-beat conditions being satisfied let the machine skip through as soon
 * as the blocking earlier condition resolves, and the optional-beat skip
 * (the free-food beat advances when the next beat's condition is met even
 * though the player never performed it).
 *
 * <p>Field order of {@link ChickenArcTransitions.Observed}: playerHasWand,
 * campfireLit, sleepHappenedToday, wallBlockPlaced, doorPlaced,
 * roomRegistered, roomBuilt, signPlacedAsJobBoard, chestPlaced,
 * welcomeMatPlaced, <b>bonusFoodGiven</b>, playerOpenedVillagerUi,
 * playerOpenedFlagUi, worldlySeedsInAnyContainer, playerGaveSeedsToChicken,
 * handPlaced.
 */
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
        // Mat-gated arrival: the welcome-mat beat now follows the campfire beat
        // directly, so a lit campfire cascades to the mat beat, not the sleep.
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
        // Out-of-order: wand + campfire-lit are met (campfire is a later beat),
        // but the wall beat that now follows STICK is unmet, so the machine stops
        // there instead of skipping forward to the campfire/sunset beats.
        ChickenArcTransitions.Observed obs = new ChickenArcTransitions.Observed(
                true, true, false, false, false, false, false, false, false, false, false, false, false, false, false, false);
        ChickenBeatState next = ChickenArcTransitions.advance(
                ChickenBeatState.WAITING_FOR_STICK, obs
        );
        Assertions.assertEquals(ChickenBeatState.WAITING_FOR_WALL_BLOCK, next);
    }

    @Test
    void outOfOrder_chestAndCampfireBeforeDoor_cascadesToPressurePlate() {
        // F3-style out-of-order: the chest is placed and the campfire lit before
        // the sign beat; once the room exists the machine cascades through
        // chest + campfire to the (now) next pending beat, the mat.
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
        // The room-build beat advances only when a room is detected
        // (roomBuilt). If the door is registered but no enclosed room exists,
        // the machine must stop at WAITING_FOR_ROOM_BUILD and not skip forward
        // to the chest beat — that is the whole point of the beat: it bridges
        // "door registered" to "a room exists" by waiting for one to appear.
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
        // Out-of-order: the door is registered (roomRegistered=true) but no room
        // is built yet (roomBuilt=false), so the machine stops at
        // WAITING_FOR_ROOM_BUILD instead of skipping to the chest beat — even
        // though the chest beat's own condition is also met (chestPlaced=true).
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
        // Even with every flag true, the machine must end at COMPLETE and
        // never fall through to FORFEIT's ordinal.
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

    // --- Optional-beat (free-food) behaviour ------------------------------

    @Test
    void freeFood_beat_stalls_whenBonusNotGivenAndNextNotMet() {
        // Optional beat, own condition false and next (villager-UI) not met:
        // the arc must stay at the free-food beat.
        ChickenArcTransitions.Observed obs = allFalse();
        ChickenBeatState next = ChickenArcTransitions.advance(
                ChickenBeatState.WAITING_FOR_FREE_FOOD, obs
        );
        Assertions.assertEquals(ChickenBeatState.WAITING_FOR_FREE_FOOD, next);
    }

    @Test
    void freeFood_beat_skips_whenNextBeatMet() {
        // Player opened the villager UI but never gave the bonus: the optional
        // free-food beat auto-advances, so the arc is not stalled at it. The
        // next beat after the villager-UI is now the seeds-delivery beat
        // (the flag-UI beat moved ahead of the sleep).
        ChickenArcTransitions.Observed obs = new ChickenArcTransitions.Observed(
                false, false, false, false, false, false, false, false, false, false, false, true, false, false, false, false);
        ChickenBeatState next = ChickenArcTransitions.advance(
                ChickenBeatState.WAITING_FOR_FREE_FOOD, obs
        );
        Assertions.assertEquals(ChickenBeatState.AWAITING_WORLDLY_SEEDS_DELIVERY, next);
    }

    @Test
    void freeFood_beat_completes_whenBonusGiven() {
        // The player gave the bonus: the optional beat completes normally.
        ChickenArcTransitions.Observed obs = new ChickenArcTransitions.Observed(
                false, false, false, false, false, false, false, false, false, false, true, false, false, false, false, false);
        ChickenBeatState next = ChickenArcTransitions.advance(
                ChickenBeatState.WAITING_FOR_FREE_FOOD, obs
        );
        Assertions.assertEquals(ChickenBeatState.WAITING_FOR_VILLAGER_UI, next);
    }
}
