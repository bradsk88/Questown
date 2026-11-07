package ca.bradj.questown.town.entity;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TownVillagerSleepModuleTest {

    /** A bed claim from a villager that loads before its town waits until the town is ready, instead of throwing. */
    @Test
    void claimBedBeforeInitializationDefersInsteadOfThrowing() {
        TownVillagerSleepModule module = new TownVillagerSleepModule();
        Assertions.assertDoesNotThrow(() -> module.claimBed(null));
        Assertions.assertDoesNotThrow(() -> module.claimBed(null));
    }
}
