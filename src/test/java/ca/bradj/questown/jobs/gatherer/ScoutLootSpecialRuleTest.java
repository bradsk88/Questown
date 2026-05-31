package ca.bradj.questown.jobs.gatherer;

import ca.bradj.questown.integration.jobs.AfterExtractEvent;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Guards {@link ScoutLootSpecialRule}'s no-op case: when nothing was extracted the rule
 * scouts nothing and never dereferences the world.
 *
 * <p>Testability gap (intentional): the non-map-item guard and the happy path (map
 * extracted → roll loot → register) both dereference {@code ItemsInit.GATHERER_MAP} and
 * the server's loot tables, neither of which is available in a plain unit test (the mod
 * registry and a {@code ServerLevel} aren't initialized). Those paths are covered by the
 * in-game gatherer/explorer flow. Verified here: the early return when no item is present.
 */
class ScoutLootSpecialRuleTest {

    private static final BlockPos ORIGIN = BlockPos.ZERO;

    @Test
    void noExtractedItem_doesNotScout() {
        boolean[] called = {false};
        AfterExtractEvent<Boolean> event = new AfterExtractEvent<>(
                null, ORIGIN, ORIGIN,
                (ctx, data) -> ctx,
                (ctx, hunger) -> ctx,
                (ctx, effect, dur) -> ctx,
                null, // no product extracted
                (ctx, foundLoot) -> {
                    called[0] = true;
                    return ctx;
                }
        );

        new ScoutLootSpecialRule().afterExtract(true, event);

        Assertions.assertFalse(called[0]);
    }
}
