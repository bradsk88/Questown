package ca.bradj.questown.jobs.special;

import ca.bradj.questown.QT;
import ca.bradj.questown.integration.jobs.AfterInsertItemEvent;
import ca.bradj.questown.integration.jobs.BeforeExtractEvent;
import ca.bradj.questown.integration.jobs.JobPhaseModifier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class PlantSeedsSpecialRule implements
        JobPhaseModifier {
    @Override
    public <X> X beforeExtract(
            X context,
            BeforeExtractEvent<X> event
    ) {
        return context;
    }

    @Override
    public <CONTEXT> @Nullable CONTEXT afterInsertItem(
            CONTEXT ctxInput,
            AfterInsertItemEvent event
    ) {
        BlockPos groundPos = event.workSpot().position();
        ServerLevel level = event.level();

        BlockHitResult bhr = new BlockHitResult(
                Vec3.atCenterOf(groundPos), Direction.UP,
                groundPos, false
        );
        InteractionResult result = Items.WHEAT_SEEDS.useOn(new UseOnContext(
                level, null, InteractionHand.MAIN_HAND,
                Items.WHEAT_SEEDS.getDefaultInstance(), bhr
        ));
        if (!result.consumesAction()) {
            QT.JOB_LOGGER.error(
                    "Failed to plant seed {} at {}",
                    event.inserted(), groundPos
            );
        }
        return null;
    }
}
