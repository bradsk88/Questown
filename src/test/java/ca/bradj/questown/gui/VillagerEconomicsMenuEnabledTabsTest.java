package ca.bradj.questown.gui;

import ca.bradj.questown.core.network.OpenVillagerMenuMessage;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** With the economics pane open, only the economics tab shows as selected: its tab list is {@link VillagerTabs#except(String)}. */
class VillagerEconomicsMenuEnabledTabsTest {

    @SuppressWarnings("unchecked")
    private static Collection<String> enabledTabs() throws Exception {
        Field f = VillagerEconomicsMenu.class.getDeclaredField("ENABLED_TABS");
        f.setAccessible(true);
        return (Collection<String>) f.get(null);
    }

    @Test
    void economics_pane_enables_the_change_root_tab() throws Exception {
        Collection<String> tabs = enabledTabs();
        assertTrue(tabs.contains(OpenVillagerMenuMessage.CHANGE_ROOT),
                "the economics pane must list change_root as openable, or its tab renders selected: " + tabs);
    }

    @Test
    void economics_pane_does_not_list_itself() throws Exception {
        assertFalse(enabledTabs().contains(OpenVillagerMenuMessage.ECONOMICS),
                "the open pane must not list itself as openable");
    }

    @Test
    void matches_the_sibling_pane_construction() throws Exception {
        assertEquals(VillagerTabs.except(OpenVillagerMenuMessage.ECONOMICS), enabledTabs());
    }
}
