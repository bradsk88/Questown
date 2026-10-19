package ca.bradj.questown.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VillagerBopTabTest {

    @Test
    void openedFromTheFlag_alwaysShowsATabBackToTheFlagUi() {
        // 2026-10-19 playtest: from the flag's villagers click-through, a BOP tab takes you back
        // to the flag UI.
        assertEquals(new VillagerBopTab(true, true, false), VillagerBopTab.decide(true, false));
        assertEquals(new VillagerBopTab(true, true, false), VillagerBopTab.decide(true, true));
    }

    @Test
    void openedFromTheVillager_noFlagTab() {
        // "The BOP tab should not be visible when opening villager UI via villager interaction":
        // only the villager's own earned-BOP screen, as before.
        assertEquals(new VillagerBopTab(false, false, false), VillagerBopTab.decide(false, false));
        assertEquals(new VillagerBopTab(true, false, true), VillagerBopTab.decide(false, true));
    }
}
