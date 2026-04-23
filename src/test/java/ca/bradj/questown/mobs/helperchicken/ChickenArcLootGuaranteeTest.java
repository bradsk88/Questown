package ca.bradj.questown.mobs.helperchicken;

import org.junit.jupiter.api.Assertions;
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
    void TODO_shouldPrepend_flagBased_requiresRealFlagBe() {
        Assertions.fail(
                "TODO[U7]: the three flag-BE bits driving shouldPrepend " +
                        "(chicken-ever-spawned, chicken-arc-forfeit, " +
                        "first-gather-worldly-seeds-fired) live on TownFlagBlockEntity, " +
                        "which requires Forge-registered TilesInit.TOWN_FLAG to construct. " +
                        "Covered by in-game verification: observe the first gatherer's " +
                        "first fetch produces Worldly Seeds, subsequent fetches do not, " +
                        "and the bit flips when seeds land in a town container."
        );
    }

    @Test
    void TODO_maybePrepend_prependsSingleSeeds_forActiveArc() {
        Assertions.fail(
                "TODO[U7]: maybePrepend builds a new ItemStack from " +
                        "ItemsInit.WORLDLY_SEEDS, which is null outside the Forge mod-loading " +
                        "lifecycle. Both realtime and warp wrap sites call this helper; " +
                        "warp parity is verified in-game via /_qtdev testall or an " +
                        "explicit warp cycle."
        );
    }
}
