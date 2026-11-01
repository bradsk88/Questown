package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.jobs.declarative.meta.DinerRawFoodWork;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The eat step is the work-required count at NEED_EAT: 10 at a table, 12 for the two flag diners
 * (no table, raw food).
 */
class DiningWorkDurationTest {

    @BeforeAll
    static void bootstrap() {
        // The diner classes build Ingredients in their static initializers.
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void eatingAtATable_isTwiceTheTenth() {
        assertEquals(10, DinerWork.WORK_REQUIRED_AT_STATES.get(DinerWork.BLOCK_STATE_NEED_EAT));
    }

    @Test
    void eatingWithoutATable_isTwelve() {
        assertEquals(12, DinerNoTableWork.WORK_REQUIRED_AT_STATES.get(DinerNoTableWork.BLOCK_STATE_NEED_EAT));
    }

    @Test
    void eatingRawFood_isTwelve() {
        assertEquals(12, DinerRawFoodWork.WORK_REQUIRED_AT_STATES.get(DinerRawFoodWork.BLOCK_STATE_NEED_EAT));
    }
}
