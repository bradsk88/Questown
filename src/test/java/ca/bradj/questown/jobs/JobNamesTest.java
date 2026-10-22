package ca.bradj.questown.jobs;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JobNamesTest {

    @Test
    void tripLengthsReadAsWords() {
        assertEquals("Rod (half day)", JobNames.variant("rod_half_day"));
        assertEquals("Sword (half day)", JobNames.variant("sword_half_day"));
        assertEquals("Shovel (full day)", JobNames.variant("shovel_full_day"));
        assertEquals("Axe (medium trip)", JobNames.variant("axe_med"));
        assertEquals("Axe (short trip)", JobNames.variant("axe_short"));
    }

    @Test
    void plainIdsBecomeSentenceCase() {
        assertEquals("Gather", JobNames.variant("gather"));
        assertEquals("Fill seed bin", JobNames.variant("fill_seed_bin"));
        assertEquals("Simple furnace food", JobNames.variant("simple_furnace_food"));
    }

    @Test
    void aBareLengthWordIsKept() {
        // "full" alone is a job name, not a trip length.
        assertEquals("Full", JobNames.variant("full"));
    }
}
