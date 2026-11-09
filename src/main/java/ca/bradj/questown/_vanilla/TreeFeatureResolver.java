package ca.bradj.questown._vanilla;

import ca.bradj.questown.QT;
import ca.bradj.questown.mc.Compat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import org.jetbrains.annotations.Nullable;

/** Finds the tree a sapling grows, so real-time and warp growth produce the same tree. */
public final class TreeFeatureResolver {

    private TreeFeatureResolver() {
    }

    public record Resolved(TreeFeature feature, TreeConfiguration config) {
    }

    /** The same random source for a position every time, so a sapling always grows the same tree. */
    public static RandomSource seededFor(BlockPos pos) {
        return RandomSource.create(pos.asLong());
    }

    /** The tree a sapling grows, or null if it is not a known tree. */
    public static @Nullable Resolved resolve(ItemStack sapling, RegistryAccess registryAccess) {
        ResourceLocation saplingId = Compat.getItemId(sapling.getItem());
        String[] idParts = saplingId.getPath().split("_sapling");
        if (idParts.length != 1) {
            QT.JOB_LOGGER.error("Cannot resolve tree feature for unconventional sapling id: {}", saplingId);
            return null;
        }

        var features = registryAccess.registryOrThrow(Registry.CONFIGURED_FEATURE_REGISTRY);
        String treeId = idParts[0];
        ConfiguredFeature<?, ?> cf = features.get(new ResourceLocation("minecraft", treeId));
        if (cf == null) {
            cf = features.get(new ResourceLocation("minecraft", treeId + "_tree"));
            if (cf == null) {
                QT.JOB_LOGGER.error("No configured feature found for tree: {}", treeId);
                return null;
            }
        }

        if (!(cf.feature() instanceof TreeFeature tree)) {
            QT.JOB_LOGGER.error("Configured feature for {} is not a tree feature", treeId);
            return null;
        }
        if (!(cf.config() instanceof TreeConfiguration config)) {
            QT.JOB_LOGGER.error("Configured feature for {} has a non-tree configuration", treeId);
            return null;
        }
        return new Resolved(tree, config);
    }
}
