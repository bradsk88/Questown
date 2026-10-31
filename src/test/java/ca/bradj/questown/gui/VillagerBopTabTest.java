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
    void openedFromTheVillager_withoutAnEarnedBop_leadsToTheFlagUi() {
        // Agent playtest r10 #5 (dev trial 2026-10-23, reverses the 10-19 "not visible via
        // villager interaction" ruling): the change-job panel asks for a BOP the player has to
        // take from the flag, so the card always offers a way there.
        assertEquals(new VillagerBopTab(true, true, false), VillagerBopTab.decide(false, false));
    }

    @Test
    void openedFromTheVillager_withAnEarnedBop_isTheVillagersOwnScreen() {
        assertEquals(new VillagerBopTab(true, false, true), VillagerBopTab.decide(false, true));
    }
}
