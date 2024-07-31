package ca.bradj.questown.jobs.special;

import ca.bradj.questown.QT;
import ca.bradj.questown.integration.jobs.AfterInsertItemEvent;
import ca.bradj.questown.integration.jobs.BeforeExtractEvent;
import ca.bradj.questown.integration.jobs.BeforeStateChangeEvent;
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

public class UseItemOnBlockSpecialRule implements
        JobPhaseModifier {
    @Override
    public <X> X beforeExtract(
            X context,
            BeforeExtractEvent<X> event
    ) {
        return null;
    }

    @Override
    public <CONTEXT> @Nullable CONTEXT afterInsertItem(
            CONTEXT ctxInput,
            AfterInsertItemEvent event
    ) {
        // TODO: Should this actually go in beforeStateChange (so we can ensure
        //  that any work or timers are complete)?
        //  If so, beforeStateChange will need to know which item has been
        //  inserted (or items).
        BlockPos groundPos = event.workSpot().position();
        ServerLevel level = event.level();

        BlockHitResult bhr = new BlockHitResult(
                Vec3.atCenterOf(groundPos), Direction.UP,
                groundPos, false
        );
        Item item = event.inserted().getItem();
        InteractionResult result = item.useOn(new UseOnContext(
                level, null, InteractionHand.MAIN_HAND,
                item.getDefaultInstance(), bhr
        ));
        if (!result.consumesAction()) {
            QT.JOB_LOGGER.error(
                    "Failed to use item {} on block at {}",
                    event.inserted(), groundPos
            );
        }
        return null;
    }

    @Override
    public Void beforeStateChange(BeforeStateChangeEvent event) {
        return null;
    }
}
