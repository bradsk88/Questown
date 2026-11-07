package ca.bradj.questown.town.entity;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

/** {@link ShutdownProgress}: done only when every villager is in and the minimum time has passed; the force deadline catches stragglers. */
class ShutdownProgressTest {

    private static final long START = 1000L;
    private static final long FLOOR = 200L;
    private static final long FORCE = 600L;

    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();

    private ShutdownProgress twoTownieRitual() {
        return new ShutdownProgress(Set.of(a, b), START, FLOOR, FORCE);
    }

    @Test
    void notCompleteWhileTowniesRemainUnabsorbed() {
        ShutdownProgress p = twoTownieRitual();
        p.markAbsorbed(a);
        Assertions.assertFalse(p.isComplete(START + FLOOR + 100));
    }

    @Test
    void notCompleteBeforeFloorEvenWhenAllAbsorbed() {
        ShutdownProgress p = twoTownieRitual();
        p.markAbsorbed(a);
        p.markAbsorbed(b);
        // Everyone home, but the minimum time hasn't passed.
        Assertions.assertFalse(p.isComplete(START + FLOOR - 1));
    }

    @Test
    void completeOnceRosterAbsorbedAndFloorElapsed() {
        ShutdownProgress p = twoTownieRitual();
        p.markAbsorbed(a);
        p.markAbsorbed(b);
        Assertions.assertTrue(p.isComplete(START + FLOOR));
    }

    @Test
    void emptyTownStillWaitsTheFloor() {
        ShutdownProgress p = new ShutdownProgress(Set.of(), START, FLOOR, FORCE);
        Assertions.assertFalse(p.isComplete(START + FLOOR - 1));
        Assertions.assertTrue(p.isComplete(START + FLOOR));
    }

    @Test
    void forceAbsorbDeadlineIsTheDeadlockGuard() {
        ShutdownProgress p = twoTownieRitual();
        Assertions.assertFalse(p.forceAbsorbDue(START + FORCE - 1));
        Assertions.assertTrue(p.forceAbsorbDue(START + FORCE));
    }
}
