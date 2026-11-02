package ca.bradj.questown.jobs.gatherer;

import ca.bradj.questown.integration.jobs.AfterExtractEvent;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** {@link ScoutLootSpecialRule} does nothing when nothing was extracted. The map path needs the mod's items and a server, so the game covers it. */
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
                null,
                (ctx, foundLoot) -> {
                    called[0] = true;
                    return ctx;
                }
        );

        new ScoutLootSpecialRule().afterExtract(true, event);

        Assertions.assertFalse(called[0]);
    }
}
