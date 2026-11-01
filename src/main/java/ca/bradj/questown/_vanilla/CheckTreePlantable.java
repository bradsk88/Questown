package ca.bradj.questown._vanilla;

import ca.bradj.questown.integration.jobs.JobPhaseModifier;
import ca.bradj.questown.integration.jobs.QTNativeRule;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.jobs.JobBlockTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SaplingBlock;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Tier 1 ({@link QTNativeRule}). Confirms that a held/town sapling can grow at the workspot:
 * a 3x3 air gap above, then the real plantability check via {@code canTreeGrowAt} — which runs
 * {@code TreeFeature.place} behind the {@code QTWorldAccess} seam (a {@code VoidLevel} dry run in
 * realtime, a {@code SnapshotWorldGenLevel} dry run in warp). No {@code asServerLevel()} here.
 */
public class CheckTreePlantable extends JobPhaseModifier implements QTNativeRule {

    @Override
    public boolean postJobBlockCheckPassed(
            JobBlockTestContext ctx
    ) {
        boolean b = super.postJobBlockCheckPassed(ctx);
        if (!b) {
            return false;
        }
        Collection<MCHeldItem> items = ctx.townUniqueItems().get().stream().map(MCHeldItem::fromTown).toList();
        ItemStack sapling = getSapling(items);
        if (sapling == null) {
            sapling = getSapling(ctx.heldItems().get());
        }
        if (sapling == null) {
            return false;
        }

        // Confirm that all of the blocks in a 3x3 area above the sapling are air
        BlockPos above = ctx.blockPos().above();
        Iterable<BlockPos> rg = BlockPos.betweenClosed(above.offset(-1, 0, -1), above.offset(1, 0, 1));
        for (BlockPos pos : rg) {
            if (!ctx.world().isAir(pos)) {
                return false;
            }
        }

        return ctx.world().canTreeGrowAt(above, sapling);
    }

    private @Nullable ItemStack getSapling(
            Collection<MCHeldItem> items
    ) {
        for (MCHeldItem item : items) {
            if (item.isEmpty()) {
                continue;
            }
            ItemStack stack = item.get().toMCItemStack();
            if (isSapling(stack)) {
                return stack;
            }
        }
        return null;
    }

    private boolean isSapling(ItemStack heldItem) {
        if (!(heldItem.getItem() instanceof BlockItem blockItem)) {
            return false;
        }
        Block block = blockItem.getBlock();
        return block instanceof SaplingBlock;
    }
}
