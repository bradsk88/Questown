package ca.bradj.questown.jobs.declarative;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * The LOOT quest check fires for any non-empty extraction on the live path, independent of the
 * {@code EXTRACTING_PRODUCT} special rule: the hunter/sword job declares none, and its raw-meat grant
 * must still close the raw-food LOOT quest. The live wiring is registry-bound; the
 * {@code loot_on_extract} autotest covers it end to end.
 */
class LootExtractGuardTest {

    @Test
    void nonEmptyExtract_withNoExtractingProductRule_questCheckFires() {
        // The hunter case: a raw-meat grant (mutton) where the job declares no EXTRACTING_PRODUCT
        // special rule must fire.
        Assertions.assertTrue(LootExtractGuard.questCheckFires(/*itemNonEmpty=*/true, /*jobDeclaresExtractingProductRule=*/false));
    }

    @Test
    void nonEmptyExtract_withExtractingProductRule_questCheckFires() {
        // A gatherer/cook that DOES declare the rule still fires.
        Assertions.assertTrue(LootExtractGuard.questCheckFires(true, true));
    }

    @Test
    void extractWithNoItem_questCheckNeverFires() {
        // An empty/placeholder extraction (the tryGiveItems null-item path) is
        // never eligible, regardless of the job's rules.
        Assertions.assertFalse(LootExtractGuard.questCheckFires(false, false));
        Assertions.assertFalse(LootExtractGuard.questCheckFires(false, true));
    }
}
