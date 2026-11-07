package ca.bradj.questown.jobs.special;

import ca.bradj.questown.integration.jobs.JobPhaseModifier;
import ca.bradj.questown.integration.jobs.QTNativeRule;
import ca.bradj.questown.integration.jobs.WarpTickEvent;
import ca.bradj.questown.world.QTWorldAccess;

/** Grows planted saplings into trees during a warp, after {@link #TREE_GROWTH_TICKS}, so the same warp can chop them. */
public class GrowTreesWarpRule extends JobPhaseModifier implements QTNativeRule {

    // Short enough that a warp can plant, grow and chop.
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
            // A sapling that can't grow here never will, so forget it either way.
            world.clearPlantedSapling(planted.pos());
        }

        return town;
    }
}
