package ca.bradj.questown.core;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

class UtilCleanAllPassTest {

    @Test
    void noRules_pass_soAJobBlockWithNoSpecialRulesIsUsable() {
        Assertions.assertTrue(UtilClean.allPass(List.<Boolean>of(), b -> b));
    }

    @Test
    void everyRulePassing_passes() {
        Assertions.assertTrue(UtilClean.allPass(List.of(true, true), b -> b));
    }

    @Test
    void anyRuleFailing_fails_soOneRestrictionBlocksTheWork() {
        Assertions.assertFalse(UtilClean.allPass(List.of(true, false), b -> b));
        Assertions.assertFalse(UtilClean.allPass(List.of(false, true), b -> b));
    }

    @Test
    void rulesAfterAFailure_areNotChecked() {
        List<Integer> checked = new ArrayList<>();
        UtilClean.allPass(List.of(1, 2, 3), r -> {
            checked.add(r);
            return r != 2;
        });
        Assertions.assertEquals(List.of(1, 2), checked);
    }
}
