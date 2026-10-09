package ca.bradj.questown.core;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Freshness gate of {@code isJobBlock} (village/self_sufficiency, gatherer leaver soft-locks at
 * the welcome mat in ~50% of worlds, e.g. 810/811 vs passing 807/808/809).
 *
 * <p>The gate: a job block is usable when {@code jobBlockAlreadyUsed} (works recently OR already
 * inserted) OR, for a fresh block, when it passes {@code trySpecialRuleIsJobBlock}. The gather leaver
 * has ZERO global special rules and has not worked yet (fresh), so the whole gate reduces to
 * {@code trySpecialRuleIsJobBlock}, whose loop returns {@code false} for an EMPTY rule list
 * (the initial {@code passed=false} is never flipped).
 *
 * <p>Chicken-and-egg: a fresh leaver is never a job block, so it can never work the first time to
 * set "worked recently"; the gate then depends on some other leaver having worked. That per-leaver
 * timing is the 50/50 flake.
 *
 * <p>This tests the Forge-free seam {@link UtilClean#specialRulesAllPass(List)} that
 * {@code ResourceJobLoader.trySpecialRuleIsJobBlock} now delegates its pass/fail decision to
 * (same rule order, same first-failure short-circuit; only the empty-rule-set result differs,
 * in the fixed direction). RED case: an empty rule set must not block a fresh job block  --
 * "no rules declared" should mean "no restriction", not "denied".
 */
class JobBlockFreshGateTest {

    /** RED→GREEN: fresh (no-work) job with no global special rules must be usable.
     * The pre-fix loop returned {@code false} for an empty rule list, so a fresh gather
     * leaver was permanently rejected as a job block and could not bootstrap its first
     * work cycle — the 50/50 flake (810/811 vs passing 807/808/809).
     */
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

    /** GREEN (documents intended behaviour): all declared rules pass -> usable. */
    @Test
    void allRules_pass_usable() {
        Assertions.assertTrue(UtilClean.specialRulesAllPass(List.of(true, true)));
    }

    /** GREEN (documents intended behaviour): any rule fails -> denied. */
    @Test
    void anyRule_fails_denied() {
        Assertions.assertFalse(UtilClean.specialRulesAllPass(List.of(true, false)));
        Assertions.assertFalse(UtilClean.specialRulesAllPass(List.of(false, true)));
    }
}
