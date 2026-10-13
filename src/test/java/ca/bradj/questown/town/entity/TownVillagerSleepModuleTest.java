package ca.bradj.questown.town.entity;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TownVillagerSleepModuleTest {

    /**
     * Load-time registration (VisitorMobEntity.load → TownVillagerMobs.assumeStateFromTown →
     * TownVillagerHandle.register → SimpleVillagerHandle.add → claimBed) out-runs the
     * flag's initializer: the town handle is not associated yet. Pre-fix, claimBed called
     * UnsafeTown.getUnsafe() and threw "Town has not been initialized", so vanilla aborted
     * the entity load and dropped the loading entity - observed 2026-10-14: exactly one
     * townie/visitor lost on every restart (one "Exception loading entity" per boot).
     *
     * <p>Post-fix the claim defers to associate(), which drains the pending queue once the
     * flag initializes. A null entity stands in for a VisitorMobEntity: the deferred path
     * must not touch the town or the entity at all, so no entity state is read. The drain
     * path (associate with a live TownFlagBlockEntity) is registry-bound and covered by
     * held runs instead.
     */
    @Test
    void claimBedBeforeInitializationDefersInsteadOfThrowing() {
        TownVillagerSleepModule module = new TownVillagerSleepModule();
        Assertions.assertDoesNotThrow(() -> module.claimBed(null));
        Assertions.assertDoesNotThrow(() -> module.claimBed(null));
    }
}
