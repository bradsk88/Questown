package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.jobs.declarative.meta.DinerRawFoodWork;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Dev ruling (2026-10-19 playtest): "Villagers take way too long to eat ... Should take more like
 * 1/10th the time it currently does." The eat step is the work-required count at NEED_EAT; these
 * pin the tenth (50 -> 5 at a table, 25 -> 3 without one / for raw food).
 */
class DiningWorkDurationTest {

    @BeforeAll
    static void bootstrap() {
        // The diner classes build Ingredients in their static initializers.
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void eatingAtATable_isATenthOfTheOldWork() {
        assertEquals(5, DinerWork.WORK_REQUIRED_AT_STATES.get(DinerWork.BLOCK_STATE_NEED_EAT));
    }

    @Test
    void eatingWithoutATable_isATenthOfTheOldWork() {
        assertEquals(3, DinerNoTableWork.WORK_REQUIRED_AT_STATES.get(DinerNoTableWork.BLOCK_STATE_NEED_EAT));
    }

    @Test
    void eatingRawFood_isATenthOfTheOldWork() {
        assertEquals(3, DinerRawFoodWork.WORK_REQUIRED_AT_STATES.get(DinerRawFoodWork.BLOCK_STATE_NEED_EAT));
    }
}
