package ca.bradj.questown.town.workstatus;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A welcome mat's state 0 is always seeded for a trip villager. */
class WelcomeMatSeedStateTest {

    @Test
    void isWelcomeMat_recognizesMatByBlockId() {
        assertTrue(State.isWelcomeMat("questown:welcome_mat_block"));
        assertTrue(State.isWelcomeMat("questown:welcome_mat"));
        assertTrue(State.isWelcomeMat("some_mod:some_welcome_mat_variant"));
    }

    @Test
    void isWelcomeMat_rejectsNonMatAndNull() {
        assertFalse(State.isWelcomeMat("questown:job_board"));
        assertFalse(State.isWelcomeMat("minecraft:furnace"));
        assertFalse(State.isWelcomeMat("minecraft:air"));
        assertFalse(State.isWelcomeMat("questown:"));
        assertFalse(State.isWelcomeMat(null));
    }

    @Test
    void welcomeMatSeedState_isNonNull_andStateZero_needsSupplies() {
        State seed = State.welcomeMatSeedState();
        assertNotNull(seed, "a leaver's mat must always be seeded — a null seed is the soft-lock");
        assertEquals(0, seed.processingState(), "the leaver's mat starts at state 0 (needs their supplies)");
        assertEquals(0, seed.ingredientCount());
        assertEquals(0, seed.workLeft());
        assertTrue(seed.isFresh());
    }

    @Test
    void welcomeMatSeedState_isFresh() {
        assertEquals(State.fresh(), State.welcomeMatSeedState());
    }

}
