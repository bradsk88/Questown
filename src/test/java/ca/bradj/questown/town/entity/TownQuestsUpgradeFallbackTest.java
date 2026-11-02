package ca.bradj.questown.town.entity;

import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.core.VillagerUUID;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** An upgrade reward with nothing to upgrade posts a random batch instead, so the town always has quests. */
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
