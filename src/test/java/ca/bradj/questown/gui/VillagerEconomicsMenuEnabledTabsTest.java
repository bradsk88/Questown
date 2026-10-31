package ca.bradj.questown.gui;

import ca.bradj.questown.core.network.OpenVillagerMenuMessage;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2026-10-14 playtest: with the economics pane open, the "switch job"
 * (change_root) tab also rendered as selected. A tab renders selected when its
 * open-fn is null, and the open-fn is null exactly when the pane's
 * {@code getEnabledTabs()} omits that tab's type. The economics menu used a
 * hand-rolled enabled list that omitted CHANGE_ROOT (every sibling pane uses
 * {@link VillagerTabs#except(String)}), so the change_root tab got a null fn
 * alongside the economics one.
 *
 * <p>The list is a private static on the menu class (the menu's constructor is
 * registry-bound and needs a Forge mod-load harness), so the pin reads the
 * field reflectively. The static initializer itself is pure string math
 * ({@code VillagerTabs.except(ECONOMICS)}), safe to load in a unit JVM.
 */
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
        // Every other villager pane builds its list as except(ownTab); the economics
        // pane must hold the same invariant, not a hand-maintained subset.
        assertEquals(VillagerTabs.except(OpenVillagerMenuMessage.ECONOMICS), enabledTabs());
    }
}
