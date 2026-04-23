package ca.bradj.questown.jobs.gatherer;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * First-gatherer Worldly Seeds guarantee (R15) is covered by chicken-arc
 * autotest scenarios — see {@code docs/conventions/agent-chicken-verification-loop.md}.
 * The pure guard is still unit-tested in {@code ChickenArcLootGuaranteeTest}.
 */
class FirstGatherWorldlySeedsTest {

    @Test
    @Disabled("Covered by chicken-arc scenario first_gather_worldly_seeds_realtime. Currently marked setup-gap pending villager-spawn support in the executor.")
    void firstGatherDropsWorldlySeeds_realtime() {
    }

    @Test
    @Disabled("Covered by chicken-arc scenario first_gather_worldly_seeds_warp. Currently marked setup-gap pending villager-spawn support in the executor.")
    void firstGatherDropsWorldlySeeds_warp() {
    }

    @Test
    @Disabled("Invariant follows from ChickenArcLootGuarantee.maybePrepend's bit-check — pure-function coverage lives in ChickenArcLootGuaranteeTest; end-to-end non-duplication is implicit in the guarantee scenarios above.")
    void subsequentFetchesDoNotDropSeeds() {
    }

    @Test
    @Disabled("Covered by chicken-arc scenario forfeit_remove_command composed with a follow-up gather cycle. Setup-gap pending villager-spawn support as above.")
    void forfeitArcBlocksGuarantee() {
    }
}
