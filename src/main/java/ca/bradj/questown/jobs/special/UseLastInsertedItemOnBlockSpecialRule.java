package ca.bradj.questown.jobs.special;

import ca.bradj.questown.QT;
import ca.bradj.questown.integration.jobs.AfterInsertItemEvent;
import ca.bradj.questown.integration.jobs.BeforeExtractEvent;
import ca.bradj.questown.integration.jobs.BeforeMoveToNextStateEvent;
import ca.bradj.questown.integration.jobs.JobPhaseModifier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class UseLastInsertedItemOnBlockSpecialRule implements
        JobPhaseModifier {
    @Override
    public <X> X beforeExtract(
            X context,
            BeforeExtractEvent<X> event
    ) {
        BlockPos groundPos = event.workSpot();
        ServerLevel level = event.level();

        BlockHitResult bhr = new BlockHitResult(
                Vec3.atCenterOf(groundPos), Direction.UP,
                groundPos, false
        );
        Item item = event.lastInsertedItem();
        InteractionResult result = item.useOn(new UseOnContext(
                level, null, InteractionHand.MAIN_HAND,
                item.getDefaultInstance(), bhr
        ));
        if (!result.consumesAction()) {
            String msg = "Failed to use item {} on block at {}";
            QT.JOB_LOGGER.error(msg, item, groundPos);
        }
        return null;
    }

    @Override
    public <CONTEXT> @Nullable CONTEXT afterInsertItem(
            CONTEXT ctxInput,
            AfterInsertItemEvent event
    ) {
        return null;
    }

    @Override
    public Void beforeMoveToNextState(BeforeMoveToNextStateEvent event) {
        return null;
    }
}
