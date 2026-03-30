package ca.bradj.questown._vanilla;

import ca.bradj.questown.QT;
import ca.bradj.questown.integration.jobs.JobPhaseModifier;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.jobs.JobBlockTestContext;
import ca.bradj.questown.mc.Compat;
import net.minecraft.core.BlockPos;
import net.minecraft.data.BuiltinRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class CheckTreePlantable extends JobPhaseModifier {

    @Override
    public boolean postJobBlockCheckPassed(
            JobBlockTestContext ctx
    ) {
        boolean b = super.postJobBlockCheckPassed(ctx);
        if (!b) {
            return false;
        }
        Collection<MCHeldItem> items = ctx.townUniqueItems().get().stream().map(MCHeldItem::fromTown).toList();
        SaplingBlock sapling = getSapling(items);
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

        String thisName = getClass().getSimpleName();

        ResourceLocation saplingId = Compat.getItemId(sapling);
        String[] idParts = saplingId.getPath().split("_sapling");
        if (idParts.length != 1) {
            QT.JOB_LOGGER.error("Attempted to use {} with unconventional sapling ID: {}", thisName, saplingId);
            return false;
        }

        String treeId = idParts[0];
        ConfiguredFeature<?, ?> cf = BuiltinRegistries.CONFIGURED_FEATURE.get(new ResourceLocation(
                "minecraft",
                treeId
        ));
        if (cf == null) {
            cf = BuiltinRegistries.CONFIGURED_FEATURE.get(new ResourceLocation("minecraft", treeId + "_tree"));
            if (cf == null) {
                QT.JOB_LOGGER.error("Attempted to use {} with non-existent feature: {}", thisName, treeId);
                return false;
            }
        }

        if (!(cf.feature() instanceof TreeFeature tree)) {
            QT.JOB_LOGGER.error("Attempted to use {} with non-tree feature: {}", thisName, treeId);
            return false;
        }

        if (!(cf.config() instanceof TreeConfiguration config)) {
            QT.JOB_LOGGER.error("Attempted to use {} with non-tree configuration: {}", thisName, treeId);
            return false;
        }

        // TreeFeature.place() is inherently MC-coupled (runs world-gen logic to test
        // plantability). There is no QTWorldAccess abstraction for this - it intentionally
        // stays as an asServerLevel() call. During warp, asServerLevel() returns null and
        // this check is skipped (handled by the null guard above).
        ServerLevel level = ctx.world().asServerLevel();
        return tree.place(config, new VoidLevel(level), null, level.random, above);
    }

    private @Nullable SaplingBlock getSapling(
            Collection<MCHeldItem> items
    ) {
        for (MCHeldItem item : items) {
            if (item.isEmpty()) {
                continue;
            }
            SaplingBlock townSapling = getSaplingBlock(item.get().toMCItemStack());
            if (townSapling != null) {
                return townSapling;
            }
        }
        return null;
    }

    private @Nullable SaplingBlock getSaplingBlock(
            ItemStack heldItem
    ) {
        if (!(heldItem.getItem() instanceof BlockItem blockItem)) {
            return null;
        }
        Block block = blockItem.getBlock();
        if (!(block instanceof SaplingBlock sb)) {
            return null;
        }
        return sb;
    }
}
