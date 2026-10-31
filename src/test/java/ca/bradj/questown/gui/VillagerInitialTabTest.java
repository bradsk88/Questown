package ca.bradj.questown.gui;

import ca.bradj.questown.core.network.OpenVillagerMenuMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Playtest 2026-10-17 round 5: a villager menu opened by the player starts on the change
 * profession tab while that tab carries its red dot.
 */
class VillagerInitialTabTest {

    @Test
    void changeRootDot_opensChangeRoot() {
        assertEquals(OpenVillagerMenuMessage.CHANGE_ROOT, VillagerTabs.initialTab(true, true));
    }

    @Test
    void changeRootDot_opensChangeRoot_inManagementView() {
        assertEquals(OpenVillagerMenuMessage.CHANGE_ROOT, VillagerTabs.initialTab(true, false));
    }

    @Test
    void noDot_opensInventory() {
        assertEquals(OpenVillagerMenuMessage.INVENTORY, VillagerTabs.initialTab(false, true));
    }

    @Test
    void noDot_managementView_opensStats() {
        assertEquals(OpenVillagerMenuMessage.STATS, VillagerTabs.initialTab(false, false));
    }

    @Test
    void initialIsNotATab() {
        assertFalse(VillagerTabs.all().contains(OpenVillagerMenuMessage.INITIAL));
    }
}
