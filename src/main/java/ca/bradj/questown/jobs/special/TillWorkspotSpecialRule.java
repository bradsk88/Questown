package ca.bradj.questown.jobs.special;

import ca.bradj.questown.integration.jobs.*;
import ca.bradj.questown.world.QTToolAction;
import ca.bradj.questown.world.QTWorldAccess;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import ca.bradj.questown.integration.jobs.QTNativeRule;

public class TillWorkspotSpecialRule extends
        JobPhaseModifier implements QTNativeRule {
    @Override
    public <X> @Nullable X beforeExtract(
            X context,
            BeforeExtractEvent<X> event
    ) {
        QTWorldAccess world = event.world();
        BlockPos tillable = findTillableBlock(world, event);
        if (tillable == null) {
            return null;
        }
        world.applyToolTransformation(tillable, QTToolAction.HOE_TILL);
        return context;
    }

    private static <X> @Nullable BlockPos findTillableBlock(
            QTWorldAccess world,
            BeforeExtractEvent<X> event
    ) {
        BlockPos workSpot = event.workSpot();
        if (world.canToolTransformBlock(workSpot, QTToolAction.HOE_TILL)) {
            return workSpot;
        }
        List<BlockPos> candidates = world.getShuffledCopy(event.jobBlockPositions().get());
        for (BlockPos candidate : candidates) {
            if (world.canToolTransformBlock(candidate, QTToolAction.HOE_TILL)) {
                return candidate;
            }
        }
        return null;
    }

    @Override
    public <CONTEXT> @Nullable CONTEXT afterInsertItem(
            CONTEXT ctxInput,
            AfterInsertItemEvent<CONTEXT> event
    ) {
        return null;
    }

    @Override
    public Void beforeMoveToNextState(BeforeMoveToNextStateEvent event) {
        return null;
    }

    @Override
    public void beforeTick(BeforeTickEvent bxEvent) {

    }
}
