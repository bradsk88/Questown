package ca.bradj.questown.jobs;

import com.google.common.collect.ImmutableSet;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Set;

/**
 * r16 #14 (dev ruling 2026-10-24): a random Block of Progress job change prefers roots no townie
 * holds yet. r16's two rolls gave a baker and a second hunter while the town's top need had no
 * producer at all.
 */
class RootRollTest {

    private static final JobID HUNTER = new JobID("hunter", "sword");
    private static final JobID COOK = new JobID("cook", "extract");
    private static final JobID MINER = new JobID("miner", "coal");
    private static final JobID CRAFTER = new JobID("crafter", "crafter");
    private static final ImmutableSet<JobID> ALL = ImmutableSet.of(HUNTER, COOK, MINER, CRAFTER);

    @Test
    void prefersRootsNobodyHolds() {
        Set<JobID> c = RootRoll.candidates(ALL, HUNTER, Set.of("hunter", "cook"));

        Assertions.assertEquals(Set.of(MINER, CRAFTER), c);
    }

    @Test
    void everyRootHeld_fallsBackToEveryOtherRoot() {
        Set<JobID> c = RootRoll.candidates(ALL, HUNTER, Set.of("hunter", "cook", "miner", "crafter"));

        Assertions.assertEquals(Set.of(COOK, MINER, CRAFTER), c);
    }

    @Test
    void neverTheTownieOwnRoot() {
        Set<JobID> c = RootRoll.candidates(ALL, MINER, Set.of("hunter"));

        Assertions.assertFalse(c.contains(MINER));
    }
}
