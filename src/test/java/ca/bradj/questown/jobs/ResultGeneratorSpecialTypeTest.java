package ca.bradj.questown.jobs;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * {@code isResultAlwaysEmpty} becomes {@code Work.hasNoOutput}, which the "N known jobs produce
 * this" hint skips. 2026-10-20 playtest: "cooked mutton: 0 jobs produce it" — every simple-furnace
 * recipe (result type via_other_job) was flagged as producing nothing, like cook/fuel.
 */
class ResultGeneratorSpecialTypeTest {

    @Test
    void viaOtherJob_producesItsDeclaredProduct() {
        Assertions.assertFalse(ResultGenerator.forSpecialType("via_other_job").isResultAlwaysEmpty());
    }

    @Test
    void usesSpecialRules_producesNothingItself() {
        Assertions.assertTrue(ResultGenerator.forSpecialType("uses_special_rules").isResultAlwaysEmpty());
    }

    @Test
    void neitherYieldsItemsFromItsOwnCycle() {
        Assertions.assertFalse(ResultGenerator.forSpecialType("via_other_job").generate(null, java.util.List.of()).iterator().hasNext());
        Assertions.assertFalse(ResultGenerator.forSpecialType("uses_special_rules").generate(null, java.util.List.of()).iterator().hasNext());
    }
}
