package ca.bradj.questown.town.entity;

import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.core.VillagerUUID;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * r16 #16 (dev ruling 2026-10-24: fix now): a completion reward that rolls an upgrade quest for a
 * villager with nothing to upgrade logged "The flag entity will generate a random batch instead."
 * and queued nothing, so the town sat with zero quests (village/growth stalled at roster 4).
 * <p>
 * Only the "no completed quests" branch is pinned here. The "no upgrade recipe" branch reads the
 * level's recipe manager ({@code getUpgradeRecipe}), which is registry-bound; it shares the same
 * fallback call.
 */
class TownQuestsUpgradeFallbackTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void villagerWithNoCompletedQuests_queuesARandomBatchRequest() {
        TownInterface town = Mockito.mock(TownInterface.class, Mockito.RETURNS_DEEP_STUBS);
        TownQuests quests = new TownQuests();

        TownQuests.addUpgradeQuest(town, quests, VillagerUUID.random());

        Assertions.assertEquals(1, quests.pendingBatchRequests());
    }
}
