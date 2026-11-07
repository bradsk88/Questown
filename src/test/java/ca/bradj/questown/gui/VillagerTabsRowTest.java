package ca.bradj.questown.gui;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Which tabs the villager menu draws. A tab the menu leaves out is omitted, not drawn as lit. */
class VillagerTabsRowTest {

    private static final Runnable OPEN = () -> {
    };

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static List<String> titles(boolean showInventoryTab) {
        Runnable inv = showInventoryTab ? OPEN : null;
        return VillagerTabs.build(inv, OPEN, null, OPEN, OPEN, OPEN, OPEN, true, false, true, showInventoryTab, true, false)
                .stream().map(Tab::titleKey).toList();
    }

    private static List<String> litTitles(boolean showInventoryTab) {
        Runnable inv = showInventoryTab ? OPEN : null;
        return VillagerTabs.build(inv, OPEN, null, OPEN, OPEN, OPEN, OPEN, true, false, true, showInventoryTab, true, false)
                .stream().filter(Tab::selected).map(Tab::titleKey).toList();
    }

    @Test
    void managementOnly_omitsTheInventoryTab() {
        assertEquals(List.of("tooltips.stats"), litTitles(false));
        assertEquals(false, titles(false).contains("tooltips.inventory"));
    }

    @Test
    void fullMenu_keepsTheInventoryTab() {
        assertEquals(true, titles(true).contains("tooltips.inventory"));
        assertEquals(List.of("tooltips.stats"), litTitles(true));
    }

    @Test
    void hiddenChangeRoot_omitsTheChangeJobTab() {
        List<String> titles = VillagerTabs.build(OPEN, OPEN, null, OPEN, OPEN, OPEN, OPEN, true, false, true, true, false, false)
                .stream().map(Tab::titleKey).toList();
        assertEquals(false, titles.contains("tooltips.change_root"));
        assertEquals(true, titles(true).contains("tooltips.change_root"));
    }

    @Test
    void noSkillsYet_showsALockedSkillsTab_notLit() {
        List<Tab> tabs = VillagerTabs.build(OPEN, OPEN, null, null, OPEN, OPEN, OPEN, true, false, false, true, true, false);
        Tab skills = tabs.stream().filter(t -> t.titleKey().startsWith("tooltips.skills")).findFirst().orElseThrow();
        assertEquals("tooltips.skills.locked", skills.titleKey());
        assertEquals(false, skills.selected());
    }

    @Test
    void skillsPane_leavesOutItsOwnTab() {
        assertEquals(false, VillagerTabs.skillsPaneTabs(true).contains(ca.bradj.questown.core.network.OpenVillagerMenuMessage.SKILLS));
        assertEquals(false, VillagerTabs.skillsPaneTabs(false).contains(ca.bradj.questown.core.network.OpenVillagerMenuMessage.SKILLS));
        assertEquals(false, VillagerTabs.skillsPaneTabs(false).contains(ca.bradj.questown.core.network.OpenVillagerMenuMessage.INVENTORY));
    }
}
