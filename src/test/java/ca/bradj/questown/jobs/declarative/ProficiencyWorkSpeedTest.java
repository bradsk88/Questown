package ca.bradj.questown.jobs.declarative;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** A work speed times a proficiency multiplier stays within 1 to 10. */
class ProficiencyWorkSpeedTest {

    @Test
    void capsAtTen_forWellFedTownieAboveBreakEven() {
        Assertions.assertEquals(10, AbstractWorldInteraction.applyProficiencyToWorkSpeed(10, 1.16f));
    }

    @Test
    void capsAtTen_atTheMaximumMultiplier() {
        Assertions.assertEquals(10, AbstractWorldInteraction.applyProficiencyToWorkSpeed(10, 2.0f));
    }

    @Test
    void floorsAtOne_soAnUnskilledTownieStillProgresses() {
        Assertions.assertEquals(1, AbstractWorldInteraction.applyProficiencyToWorkSpeed(1, 0.5f));
    }

    @Test
    void scalesInBetween() {
        Assertions.assertEquals(6, AbstractWorldInteraction.applyProficiencyToWorkSpeed(4, 1.5f));
        Assertions.assertEquals(2, AbstractWorldInteraction.applyProficiencyToWorkSpeed(4, 0.5f));
    }
}
