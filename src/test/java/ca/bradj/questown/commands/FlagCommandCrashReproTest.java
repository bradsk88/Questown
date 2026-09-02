package ca.bradj.questown.commands;

import ca.bradj.questown.core.advancements.ApproachTownTrigger;
import ca.bradj.questown.core.init.AdvancementsInit;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Reproduction for the crash reported when running
 * {@code /qt flag X Y Z place_above} with the sister.
 *
 * <p>FlagCommand.setBlock finishes with:
 * <pre>
 * AdvancementsInit.APPROACH_TOWN_TRIGGER.trigger(
 *     source.getPlayer(), ApproachTownTrigger.Triggers.FirstVisit);
 * </pre>
 * A command source without a player (server console, command block,
 * {@code /execute ... as}) yields null from {@code source.getPlayer()}. This
 * test asserts the trigger must not throw on a null player, or the command
 * crashes the server.
 */
class FlagCommandCrashReproTest {

    @Test
    void approachTownTriggerDoesNotCrashWithNullPlayer() {
        // Register the trigger exactly the way the mod does at boot.
        try {
            AdvancementsInit.register();
        } catch (Throwable t) {
            fail("AdvancementsInit.register() threw in a plain test context: "
                + t);
        }
        assertNotNull(AdvancementsInit.APPROACH_TOWN_TRIGGER,
            "ApproachTownTrigger must be registered");

        // FlagCommand.setBlock calls trigger(source.getPlayer(), FirstVisit).
        // With a console/command-block source, getPlayer() is null.
        assertDoesNotThrow(
            () -> AdvancementsInit.APPROACH_TOWN_TRIGGER
                .trigger((ServerPlayer) null, ApproachTownTrigger.Triggers.FirstVisit),
            "ApproachTownTrigger.trigger(null, FirstVisit) must not crash the server"
        );
    }
}
