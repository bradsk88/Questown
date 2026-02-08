package ca.bradj.questown.jobs.special;

import ca.bradj.questown.integration.jobs.*;
import ca.bradj.questown.world.QTWorldAccess;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

public class TillWorkspotSpecialRule extends
        JobPhaseModifier {
    @Override
    public <X> @Nullable X beforeExtract(
            X context,
            BeforeExtractEvent<X> event
    ) {
        QTWorldAccess world = event.world();
        BlockPos groundPos = event.workSpot();
        if (!world.canToolTransformBlock(groundPos, "hoe_till")) {
            return null;
        }
        world.applyToolTransformation(groundPos, "hoe_till");
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
