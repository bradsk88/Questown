package ca.bradj.questown.jobs.declarative;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * The of-10 work speed handed to {@link ca.bradj.questown.town.workstatus.State#decrWork} must stay
 * in 1..10. Proficiency multiplies that number (ADR-0010), so the band is only safe if the product
 * is clamped — the uncapped version crashed the warp loop for a well-fed, better-than-break-even
 * townie.
 */
class ProficiencyWorkSpeedTest {

    @Test
    void capsAtTen_forWellFedTownieAboveBreakEven() {
        // The case that actually threw: max mood (base 10) and a level-0.44 multiplier of ~1.16.
        Assertions.assertEquals(10, RealtimeWorldInteraction.applyProficiencyToWorkSpeed(10, 1.16f));
    }

    @Test
    void capsAtTen_atTheMaximumMultiplier() {
        Assertions.assertEquals(10, RealtimeWorldInteraction.applyProficiencyToWorkSpeed(10, 2.0f));
    }

    @Test
    void floorsAtOne_soAnUnskilledTownieStillProgresses() {
        Assertions.assertEquals(1, RealtimeWorldInteraction.applyProficiencyToWorkSpeed(1, 0.5f));
    }

    @Test
    void scalesInBetween() {
        Assertions.assertEquals(6, RealtimeWorldInteraction.applyProficiencyToWorkSpeed(4, 1.5f));
        Assertions.assertEquals(2, RealtimeWorldInteraction.applyProficiencyToWorkSpeed(4, 0.5f));
    }
}
