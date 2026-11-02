package ca.bradj.questown.core;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/** A fresh job block with no special rules must be usable, or a new trip villager could never start its first cycle. */
class JobBlockFreshGateTest {

    @Test
    void fresh_noRules_jobBlock_mustBeUsable() {
        boolean usable = UtilClean.specialRulesAllPass(List.of());
        Assertions.assertTrue(
                usable,
                "A job block with NO declared global special rules must be usable for a fresh " +
                "(not-yet-worked) leaver  --  an empty rule set means 'no restriction', not 'denied'. " +
                "This is the gate that stalls the raw-food gather leaver in ~50% of worlds (810/811)."
        );
    }

    @Test
    void allRules_pass_usable() {
        Assertions.assertTrue(UtilClean.specialRulesAllPass(List.of(true, true)));
    }

    @Test
    void anyRule_fails_denied() {
        Assertions.assertFalse(UtilClean.specialRulesAllPass(List.of(true, false)));
        Assertions.assertFalse(UtilClean.specialRulesAllPass(List.of(false, true)));
    }
}
