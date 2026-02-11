package ca.bradj.questown.jobs.special;

import ca.bradj.questown.integration.jobs.*;
import ca.bradj.questown.world.QTToolAction;
import ca.bradj.questown.world.QTWorldAccess;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TillWorkspotSpecialRule extends
        JobPhaseModifier {
    @Override
    public <X> @Nullable X beforeExtract(
            X context,
            BeforeExtractEvent<X> event
    ) {
        QTWorldAccess world = event.world();
        BlockPos groundPos = event.workSpot();
        if (!world.canToolTransformBlock(groundPos, QTToolAction.HOE_TILL)) {
            // workSpot may be fake (warp). Try a random real job block.
            List<BlockPos> candidates = new ArrayList<>(event.jobBlockPositions().get());
            Collections.shuffle(candidates); // TODO: Shuffle via util
            for (BlockPos candidate : candidates) {
                if (world.canToolTransformBlock(candidate, QTToolAction.HOE_TILL)) {
                    groundPos = candidate;
                    break;
                }
            }
            if (!world.canToolTransformBlock(groundPos, QTToolAction.HOE_TILL)) {
                return null;
            }
        }
        world.applyToolTransformation(groundPos, QTToolAction.HOE_TILL);
        return context;
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
