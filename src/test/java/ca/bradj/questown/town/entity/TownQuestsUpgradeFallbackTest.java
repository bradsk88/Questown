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
 * A completion reward that rolls an upgrade quest for a villager with nothing to upgrade falls back
 * to a random batch, so the town is never left with zero quests.
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
