package ca.bradj.questown.town.quests;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * A batch whose reward brings a villager also asks for that villager's bed and for more storage.
 */
class ArrivalProvisionsTest {

    @Test
    void noArrival_addsNothing() {
        ArrivalProvisions p = ArrivalProvisions.plan(false, 3, 0, false);

        Assertions.assertEquals(ArrivalProvisions.NONE, p);
    }

    @Test
    void arrivalWithoutABed_asksForABedAndStorage() {
        ArrivalProvisions p = ArrivalProvisions.plan(true, 1, 0, false);

        Assertions.assertEquals(1, p.bedQuests());
        Assertions.assertTrue(p.storageQuest());
    }

    @Test
    void bedQuestsAlreadyInTheBatch_coverTheShortfall() {
        ArrivalProvisions p = ArrivalProvisions.plan(true, 2, 1, false);

        Assertions.assertEquals(1, p.bedQuests());
    }

    @Test
    void arrivalWithASpareBed_asksOnlyForStorage() {
        ArrivalProvisions p = ArrivalProvisions.plan(true, 0, 0, false);

        Assertions.assertEquals(0, p.bedQuests());
        Assertions.assertTrue(p.storageQuest());
    }

    @Test
    void storageQuestAlreadyInTheBatch_isNotRepeated() {
        ArrivalProvisions p = ArrivalProvisions.plan(true, 1, 0, true);

        Assertions.assertFalse(p.storageQuest());
    }
}
