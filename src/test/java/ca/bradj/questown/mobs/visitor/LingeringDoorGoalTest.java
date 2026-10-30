package ca.bradj.questown.mobs.visitor;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class LingeringDoorGoalTest {

    /** Standing in the doorway or beside it keeps the door open; 3 blocks away it may close. */
    @Test
    void aNearbyVillager_keepsItsDoorOpen() {
        Assertions.assertTrue(LingeringDoorGoal.keepsOpen(0));
        Assertions.assertTrue(LingeringDoorGoal.keepsOpen(2.0 * 2.0));
        Assertions.assertFalse(LingeringDoorGoal.keepsOpen(3.0 * 3.0));
    }
}
