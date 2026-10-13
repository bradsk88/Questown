package ca.bradj.questown.gui;

import ca.bradj.questown.core.network.OpenVillagerMenuMessage;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Management-only flag click-through (2026-10-14 playtest): the villager menu reached
// from the flag's villager cards hides the inventory tab. The tab strip is built from
// each menu's getEnabledTabs(), which drops INVENTORY when showInventoryTab is false.
// The drop is the pure VillagerTabs.hideInventory helper (mirroring hideSkills), pinned
// here; the payload flag round-trip is pinned in VillagerMenusPayloadTest.
class VillagerTabsHideInventoryTest {

    private static Collection<String> allTabs() {
        return VillagerTabs.except("");
    }

    @Test
    void hideInventory_drops_the_inventory_tab_only() {
        Collection<String> out = VillagerTabs.hideInventory(allTabs());
        assertFalse(out.contains(OpenVillagerMenuMessage.INVENTORY));
        // every sibling tab survives
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
