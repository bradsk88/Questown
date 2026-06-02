package ca.bradj.questown.jobs.special;

import ca.bradj.questown.integration.jobs.JobPhaseModifier;
import ca.bradj.questown.integration.jobs.QTNativeRule;
import ca.bradj.questown.integration.jobs.WarpTickEvent;
import ca.bradj.questown.world.QTWorldAccess;

/**
 * Warp-interleaved hook that grows planted saplings into real trees during time warp.
 * <p>
 * Declared as a global rule in the arborist {@code plant_sapling} JSON and collected/deduped
 * like {@link GrowCropsWarpRule}. Growth is deterministic and elapsed-time based: a sapling
 * grows once {@code currentTick - plantTick >= TREE_GROWTH_TICKS} (matching the project's
 * deterministic-warp choices, e.g. bone-meal +3, and keeping autotests non-flaky). The
 * resulting logs land in the same in-memory snapshot, so {@code cut_trees} can chop them in
 * the same warp.
 */
public class GrowTreesWarpRule extends JobPhaseModifier implements QTNativeRule {

    // Ticks a sapling must age (in warp) before it generates a tree. Small relative to a
    // typical warp window so a same-warp plant -> grow -> chop cycle can complete.
    static final long TREE_GROWTH_TICKS = 2000;

    @Override
    public <X> X onWarpTick(X town, WarpTickEvent event) {
        QTWorldAccess world = event.world();
        long currentTick = event.currentTick();

        for (QTWorldAccess.PlantedSapling planted : world.getPlantedSaplings()) {
            if (currentTick - planted.plantTick() < TREE_GROWTH_TICKS) {
                continue;
            }
            world.growTreeAt(planted.pos(), planted.sapling());
            // Drop the entry whether or not it placed: a sapling that can't grow here never will.
            world.clearPlantedSapling(planted.pos());
        }

        return town;
    }
}
