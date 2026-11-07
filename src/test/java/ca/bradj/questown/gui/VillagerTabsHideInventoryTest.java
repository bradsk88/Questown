package ca.bradj.questown.gui;

import ca.bradj.questown.core.network.OpenVillagerMenuMessage;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Opened from the flag, the villager menu has no inventory tab.
class VillagerTabsHideInventoryTest {

    private static Collection<String> allTabs() {
        return VillagerTabs.except("");
    }

    @Test
    void hideInventory_drops_the_inventory_tab_only() {
        Collection<String> out = VillagerTabs.hideInventory(allTabs());
        assertFalse(out.contains(OpenVillagerMenuMessage.INVENTORY));
        assertTrue(out.contains(OpenVillagerMenuMessage.QUESTS));
        assertTrue(out.contains(OpenVillagerMenuMessage.STATS));
        assertTrue(out.contains(OpenVillagerMenuMessage.SKILLS));
        assertTrue(out.contains(OpenVillagerMenuMessage.CHANGE_ROOT));
        assertTrue(out.contains(OpenVillagerMenuMessage.ECONOMICS));
        assertTrue(out.contains(OpenVillagerMenuMessage.BOP));
    }

    @Test
    void allTabs_contains_the_inventory_tab() {
        assertTrue(allTabs().contains(OpenVillagerMenuMessage.INVENTORY));
    }
}
