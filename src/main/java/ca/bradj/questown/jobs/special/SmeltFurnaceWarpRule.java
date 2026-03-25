package ca.bradj.questown.jobs.special;

import ca.bradj.questown.QT;
import ca.bradj.questown.integration.jobs.JobPhaseModifier;
import ca.bradj.questown.integration.jobs.WarpTickEvent;
import net.minecraft.core.BlockPos;

import java.util.Collection;
import ca.bradj.questown.integration.jobs.QTNativeRule;

public class SmeltFurnaceWarpRule extends JobPhaseModifier implements QTNativeRule {

    @Override
    public <X> X onWarpTick(X town, WarpTickEvent event) {
        Collection<BlockPos> positions = event.workBlockPositions().get();
        int ticks = (int) event.tickDelta();
        QT.FLAG_LOGGER.debug(
                "SmeltFurnaceWarpRule: {} positions, {} tickDelta",
                positions.size(), ticks
        );
        for (BlockPos pos : positions) {
            event.world().advanceProcessing(pos, ticks);
        }
        return town;
    }
}
