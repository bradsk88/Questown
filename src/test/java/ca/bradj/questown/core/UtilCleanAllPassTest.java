package ca.bradj.questown.core;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

class UtilCleanAllPassTest {

    @Test
    void noRules_pass_soAJobBlockWithNoSpecialRulesIsUsable() {
        // For example: a gatherer's welcome mat declares no job-block rules. If "no rules" read as
        // "failed", no gatherer could ever start work at the mat.
        Assertions.assertTrue(UtilClean.allPass(List.<Boolean>of(), b -> b));
    }

    @Test
    void everyRulePassing_passes() {
        // For example: a job block that needs air above it and an empty slot, and has both.
        Assertions.assertTrue(UtilClean.allPass(List.of(true, true), b -> b));
    }

    @Test
    void anyRuleFailing_fails_soOneRestrictionBlocksTheWork() {
        // For example: a furnace with air above it but no empty fuel slot is not a usable job block,
        // whichever rule is checked first.
        Assertions.assertFalse(UtilClean.allPass(List.of(true, false), b -> b));
        Assertions.assertFalse(UtilClean.allPass(List.of(false, true), b -> b));
    }

    @Test
    void rulesAfterAFailure_areNotChecked() {
        // For example: once one rule rules a block out, a slower rule after it (one that looks at the
        // world) is not run for it.
        List<Integer> checked = new ArrayList<>();
        UtilClean.allPass(List.of(1, 2, 3), r -> {
            checked.add(r);
            return r != 2;
        });
        Assertions.assertEquals(List.of(1, 2), checked);
    }
}
