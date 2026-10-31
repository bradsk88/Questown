package ca.bradj.questown.blocks;

import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.mc.Compat;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * NBT round-trip for the "earned by" job carried by a villager-earned Block of Progress stack.
 * Uses the vanilla-bootstrap pattern (no Forge registries needed) with an arbitrary vanilla item,
 * since the helpers only touch the stack's NBT.
 */
class BlockOfProgressTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void earnedBy_roundTripsThroughItemStackNBT() {
        ItemStack stack = new ItemStack(Items.STONE);
        JobID hunter = new JobID("hunter", "hunter");
        BlockOfProgress.markEarnedBy(stack, hunter);
        Assertions.assertEquals(hunter, BlockOfProgress.getEarnedBy(stack).orElseThrow());
    }

    @Test
    public void plainStack_wasEarnedByNobody() {
        Assertions.assertTrue(BlockOfProgress.getEarnedBy(new ItemStack(Items.STONE)).isEmpty());
    }

    @Test
    public void legacyGoldBop_stillNamesItsJob() {
        ItemStack stack = new ItemStack(Items.STONE);
        CompoundTag tag = new CompoundTag();
        tag.put("questown_guaranteed_job", JobID.toTag(new JobID("miner", "miner")));
        Compat.setItemDataTag(stack, tag);
        Assertions.assertEquals(new JobID("miner", "miner"), BlockOfProgress.getEarnedBy(stack).orElseThrow());
    }
}
