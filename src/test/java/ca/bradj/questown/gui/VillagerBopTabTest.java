package ca.bradj.questown.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VillagerBopTabTest {

    @Test
    void openedFromTheFlag_alwaysShowsATabBackToTheFlagUi() {
        assertEquals(new VillagerBopTab(true, true, false), VillagerBopTab.decide(true, false));
        assertEquals(new VillagerBopTab(true, true, false), VillagerBopTab.decide(true, true));
    }

    @Test
    void openedFromTheVillager_withoutAnEarnedBop_leadsToTheFlagUi() {
        // The change-job panel needs a Block of Progress from the flag, so the tab always leads there.
        assertEquals(new VillagerBopTab(true, true, false), VillagerBopTab.decide(false, false));
    }

    @Test
    void openedFromTheVillager_withAnEarnedBop_isTheVillagersOwnScreen() {
        assertEquals(new VillagerBopTab(true, false, true), VillagerBopTab.decide(false, true));
    }
}
