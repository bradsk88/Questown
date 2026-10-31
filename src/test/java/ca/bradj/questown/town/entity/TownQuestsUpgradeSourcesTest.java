package ca.bradj.questown.town.entity;

import ca.bradj.questown.town.quests.MCQuest;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Which completed room quests an upgrade roll may build on ({@code TownQuests.upgradeSources}).
 * <p>
 * Dev ruling 2026-10-24: a discard never reposts the quest just declined. Discarding a batch
 * queues a replacement; half the time that is an upgrade for the same owner, and a hunter whose
 * only completed room was a bedroom got the bedroom -> inn upgrade straight back (village/growth
 * stalled at roster 3 on a reposted inn). The replacement now skips the rooms whose upgrade was
 * declined; with nothing left it falls back to a random batch.
 */
class TownQuestsUpgradeSourcesTest {

    private static final ResourceLocation BEDROOM = new ResourceLocation("questown", "bedroom");
    private static final ResourceLocation KITCHEN = new ResourceLocation("questown", "kitchen_small");
    private static final ResourceLocation BIG_BEDROOM = new ResourceLocation("questown", "big_bedroom");

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void aDeclinedUpgradesSourceIsSkipped() {
        List<MCQuest> sources = TownQuests.upgradeSources(List.of(done(BEDROOM), done(KITCHEN)), Set.of(BEDROOM));

        Assertions.assertEquals(List.of(KITCHEN), ids(sources));
    }

    @Test
    void everySourceDeclined_leavesNothing() {
        Assertions.assertEquals(List.of(), TownQuests.upgradeSources(List.of(done(BEDROOM)), Set.of(BEDROOM)));
    }

    @Test
    void firstTierRoomsArePreferred_asBefore() {
        MCQuest upgraded = MCQuest.upgrade(UUID.randomUUID(), null, BEDROOM, BIG_BEDROOM).completed(null);

        List<MCQuest> sources = TownQuests.upgradeSources(List.of(upgraded, done(KITCHEN)), Set.of());

        Assertions.assertEquals(List.of(KITCHEN), ids(sources));
    }

    @Test
    void onlyUpgradedRoomsLeft_theyAreUsed_asBefore() {
        MCQuest upgraded = MCQuest.upgrade(UUID.randomUUID(), null, BEDROOM, BIG_BEDROOM).completed(null);

        Assertions.assertEquals(List.of(BIG_BEDROOM), ids(TownQuests.upgradeSources(List.of(upgraded), Set.of())));
    }

    @Test
    void openQuestsAreNotSources() {
        MCQuest open = MCQuest.standalone(UUID.randomUUID(), null, KITCHEN);

        Assertions.assertEquals(List.of(), TownQuests.upgradeSources(List.of(open), Set.of()));
    }

    private static MCQuest done(ResourceLocation room) {
        return MCQuest.standalone(UUID.randomUUID(), null, room).completed(null);
    }

    private static List<ResourceLocation> ids(List<MCQuest> qs) {
        return qs.stream().map(MCQuest::getWantedId).toList();
    }
}
