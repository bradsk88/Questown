package ca.bradj.questown.commands;

import ca.bradj.questown.core.advancements.ApproachTownTrigger;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/** {@code /qt flag X Y Z place_above} from a source with no player (console, command block) must not crash. */
class FlagCommandCrashReproTest {

    @Test
    void approachTownTriggerDoesNotCrashWithNullPlayer() {
        ApproachTownTrigger trigger = new ApproachTownTrigger();

        // A console source has no player.
        assertDoesNotThrow(
            () -> trigger.trigger((ServerPlayer) null, ApproachTownTrigger.Triggers.FirstVisit),
            "ApproachTownTrigger.trigger(null, FirstVisit) must not crash the server"
        );
    }
}
