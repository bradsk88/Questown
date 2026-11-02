package ca.bradj.questown.mobs.helperchicken;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** {@link ChickenArcLootGuarantee#shouldPrepend}. */
class ChickenArcLootGuaranteeTest {

    @Test
    void shouldPrepend_false_forNullFlag() {
        Assertions.assertFalse(ChickenArcLootGuarantee.shouldPrepend(null));
    }

}
