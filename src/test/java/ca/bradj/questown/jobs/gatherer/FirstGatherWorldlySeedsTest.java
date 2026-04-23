package ca.bradj.questown.jobs.gatherer;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Placeholder coverage for R15 first-gatherer Worldly Seeds guarantee across
 * realtime and warp paths. The wrap sites live in
 * {@code RealtimeWorldInteraction.getResults} and
 * {@code TimeWarpWorldInteraction.getResults}; both delegate to
 * {@code ChickenArcLootGuarantee.maybePrepend} whose guard is unit-testable
 * in isolation (see {@code ChickenArcLootGuaranteeTest}). The wrap plumbing
 * itself requires a running job pipeline.
 */
class FirstGatherWorldlySeedsTest {

    @Test
    void TODO_firstGatherDropsWorldlySeeds_realtime() {
        Assertions.fail(
                "TODO[U7]: with chicken-ever-spawned=true, chicken-arc-forfeit=false, " +
                        "first-gather-worldly-seeds-fired=false, the first gatherer's " +
                        "realtime fetch should produce Worldly Seeds at the head of the " +
                        "result list. Verified in-game via /_qtdev test gatherer."
        );
    }

    @Test
    void TODO_firstGatherDropsWorldlySeeds_warp() {
        Assertions.fail(
                "TODO[U7]: same guarantee must hold during a warp — the wrap at " +
                        "TimeWarpWorldInteraction.getResults uses the same flag-BE bits " +
                        "via this.townPos. Verified in-game via /_qtdev testall."
        );
    }

    @Test
    void TODO_subsequentFetchesDoNotDropSeeds() {
        Assertions.fail(
                "TODO[U7]: once first-gather-worldly-seeds-fired flips true (U4 " +
                        "flips it when seeds land in a container), later fetches must " +
                        "return the unmodified result set. Verified in-game."
        );
    }

    @Test
    void TODO_forfeitArcBlocksGuarantee() {
        Assertions.fail(
                "TODO[U7]: /questown chicken remove flips chicken-arc-forfeit; all " +
                        "subsequent gather results must skip the Worldly Seeds prepend. " +
                        "Verified in-game."
        );
    }
}
