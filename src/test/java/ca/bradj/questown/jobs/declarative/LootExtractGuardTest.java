package ca.bradj.questown.jobs.declarative;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** Any non-empty extraction fires the LOOT quest check, whatever the job's own rules. */
class LootExtractGuardTest {

    @Test
    void nonEmptyExtract_withNoExtractingProductRule_questCheckFires() {
        Assertions.assertTrue(LootExtractGuard.questCheckFires(/*itemNonEmpty=*/true, /*jobDeclaresExtractingProductRule=*/false));
    }

    @Test
    void nonEmptyExtract_withExtractingProductRule_questCheckFires() {
        Assertions.assertTrue(LootExtractGuard.questCheckFires(true, true));
    }

    @Test
    void extractWithNoItem_questCheckNeverFires() {
        // An empty extraction never does.
        Assertions.assertFalse(LootExtractGuard.questCheckFires(false, false));
        Assertions.assertFalse(LootExtractGuard.questCheckFires(false, true));
    }
}
