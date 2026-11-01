package ca.bradj.questown.town.entity;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TownVillagerSleepModuleTest {

    /**
     * Load-time registration (VisitorMobEntity.load → TownVillagerMobs.assumeStateFromTown →
     * TownVillagerHandle.register → SimpleVillagerHandle.add → claimBed) out-runs the flag's
     * initializer: the town handle is not associated yet. The claim defers to associate(), which
     * drains the pending queue once the flag initializes, instead of throwing (vanilla would drop
     * the loading entity). A null entity stands in for a VisitorMobEntity: the deferred path must
     * not touch the town or the entity at all. The drain path (associate with a live
     * TownFlagBlockEntity) is registry-bound.
     */
    @Test
    void claimBedBeforeInitializationDefersInsteadOfThrowing() {
        TownVillagerSleepModule module = new TownVillagerSleepModule();
        Assertions.assertDoesNotThrow(() -> module.claimBed(null));
        Assertions.assertDoesNotThrow(() -> module.claimBed(null));
    }
}
