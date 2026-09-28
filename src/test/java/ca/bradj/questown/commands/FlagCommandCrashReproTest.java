package ca.bradj.questown.commands;

import ca.bradj.questown.core.advancements.ApproachTownTrigger;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

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
 *
 * <p>The trigger is constructed directly rather than via
 * {@code AdvancementsInit.register()}: the guard under test lives in the
 * trigger's own {@code trigger()} override and needs no registry. (Criterion
 * trigger registration goes through NeoForge's dynamic registry, which is
 * only open during the mod-bus registry phase — not available in a plain
 * unit-test JVM.)
 */
class FlagCommandCrashReproTest {

    @Test
    void approachTownTriggerDoesNotCrashWithNullPlayer() {
        ApproachTownTrigger trigger = new ApproachTownTrigger();

        // FlagCommand.setBlock calls trigger(source.getPlayer(), FirstVisit).
        // With a console/command-block source, getPlayer() is null.
        assertDoesNotThrow(
            () -> trigger.trigger((ServerPlayer) null, ApproachTownTrigger.Triggers.FirstVisit),
            "ApproachTownTrigger.trigger(null, FirstVisit) must not crash the server"
        );
    }
}
