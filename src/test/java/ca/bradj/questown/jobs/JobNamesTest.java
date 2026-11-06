package ca.bradj.questown.jobs;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
        assertEquals("Full", JobNames.variant("full"));
    }

    @Test
    void aProductJobIsNamedForItsIconItem() {
        ResourceLocation shovel = new ResourceLocation("minecraft", "wooden_shovel");
        assertEquals(shovel, JobNames.product(new JobID("crafter", "wooden_shovel"), shovel));
    }

    @Test
    void aJobWhoseIconIsAnotherItemHasNoProduct() {
        assertNull(JobNames.product(new JobID("hunter", "sword_half_day"), new ResourceLocation("minecraft", "iron_sword")));
        assertNull(JobNames.product(new JobID("cook", "fuel"), new ResourceLocation("minecraft", "furnace")));
        assertNull(JobNames.product(new JobID("crafter", "stick"), null));
    }
}
