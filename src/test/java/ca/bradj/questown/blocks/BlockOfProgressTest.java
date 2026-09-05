package ca.bradj.questown.blocks;

import ca.bradj.questown.jobs.JobID;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * NBT round-trip for the bless/guarantee carried by a "gold" Block of Progress stack. Uses the
 * vanilla-bootstrap pattern (no Forge registries needed) with an arbitrary vanilla item, since
 * the bless helpers only touch the stack's NBT.
 */
class BlockOfProgressTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void blessWithoutJob_isBlessed_butGuaranteesNothing() {
        ItemStack stack = new ItemStack(Items.STONE);
        BlockOfProgress.bless(stack);
        Assertions.assertTrue(BlockOfProgress.isBlessed(stack));
        Assertions.assertTrue(BlockOfProgress.getBlessedJob(stack).isEmpty());
    }

    @Test
    public void blessWithJob_roundTripsThroughItemStackNBT() {
        ItemStack stack = new ItemStack(Items.STONE);
        JobID hunter = new JobID("hunter", "hunter");
        BlockOfProgress.bless(stack, hunter);

        Assertions.assertTrue(BlockOfProgress.isBlessed(stack));
        Assertions.assertEquals(hunter, BlockOfProgress.getBlessedJob(stack).orElseThrow());
    }

    @Test
    public void plainStack_isNotBlessed_andGuaranteesNothing() {
        ItemStack stack = new ItemStack(Items.STONE);
        Assertions.assertFalse(BlockOfProgress.isBlessed(stack));
        Assertions.assertTrue(BlockOfProgress.getBlessedJob(stack).isEmpty());
    }
}
