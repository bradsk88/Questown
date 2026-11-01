package ca.bradj.questown.commands;

import ca.bradj.questown.core.advancements.ApproachTownTrigger;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * {@code /qt flag X Y Z place_above} from a source with no player (the console, a command block,
 * {@code /execute ... as}) must not crash: FlagCommand.setBlock fires the approach-town trigger
 * with {@code source.getPlayer()}, which is null there. The trigger is constructed directly; the
 * guard lives in its own {@code trigger()} override.
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
