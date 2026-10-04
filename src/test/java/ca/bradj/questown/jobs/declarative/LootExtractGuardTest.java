package ca.bradj.questown.jobs.declarative;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Pins the LOOT-quest-close wiring contract the 2026-10-13 fix rests on.
 *
 * <p>The bug: the LOOT quest check ({@code Quests.processLootQuests}) was
 * nested inside {@code RealtimeWorldInteraction}'s 5-arg {@code postExtractHook},
 * which is only reached via the 3-arg base bridge when the job declares an
 * {@code EXTRACTING_PRODUCT} special rule. The hunter/sword job declares none,
 * so its raw-meat grant never run the check and the raw-food LOOT quest
 * (villager_simple_furnace_food_raw) never closed. The fix decouples the quest
 * check from that gate: it fires for any non-empty extraction on the live path,
 * independent of the special rule. The live-engine wiring (RealtimeWorldInteraction
 * + the declarative engine) is registry-bound and not unit-loadable, so this
 * pins the decoupling decision it is built on; the end-to-end behaviour is
 * verified by the {@code loot_on_extract} autotest.
 */
class LootExtractGuardTest {

    @Test
    void nonEmptyExtract_withNoExtractingProductRule_questCheckFires() {
        // The hunter case: a raw-meat grant (mutton) where the job declares no
        // EXTRACTING_PRODUCT special rule. Before the fix this returned false
        // (the quest check was skipped); it must fire now.
        Assertions.assertTrue(LootExtractGuard.questCheckFires(/*itemNonEmpty=*/true, /*jobDeclaresExtractingProductRule=*/false));
    }

    @Test
    void nonEmptyExtract_withExtractingProductRule_questCheckFires() {
        // A gatherer/cook that DOES declare the rule still fires (regression
        // guard: the fix must not rely on the rule being absent).
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
