package ca.bradj.questown.mobs.helperchicken;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Covers the pure guard in {@link ChickenArcLootGuarantee#shouldPrepend}.
 *
 * <p>The {@code maybePrepend} function constructs a {@code WorldlySeeds}
 * {@link net.minecraft.world.item.ItemStack} from the mod-registered item
 * registry and so cannot run without Forge — covered by a TODO_ test below.
 */
class ChickenArcLootGuaranteeTest {

    @Test
    void shouldPrepend_false_forNullFlag() {
        Assertions.assertFalse(ChickenArcLootGuarantee.shouldPrepend(null));
    }

    @Test
    @Disabled("Covered by chicken-arc scenarios first_gather_worldly_seeds_realtime and first_gather_worldly_seeds_warp. See docs/conventions/agent-chicken-verification-loop.md.")
    void shouldPrepend_flagBased_requiresRealFlagBe() {
    }

    @Test
    @Disabled("Covered by chicken-arc scenarios first_gather_worldly_seeds_realtime and first_gather_worldly_seeds_warp.")
    void maybePrepend_prependsSingleSeeds_forActiveArc() {
    }
}
