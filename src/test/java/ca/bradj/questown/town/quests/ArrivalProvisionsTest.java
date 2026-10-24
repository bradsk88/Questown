package ca.bradj.questown.town.quests;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * r16 #11 / #17 (dev ruling 2026-10-24): a batch whose reward brings a villager also asks for that
 * villager's bed and for more storage. The bed shortfall counted only townies already in town, so
 * the miner batch's Villager 4 arrived bedless and storage filled with no quest asking for chests.
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
