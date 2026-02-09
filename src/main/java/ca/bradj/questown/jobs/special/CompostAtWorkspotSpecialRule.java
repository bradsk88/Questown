package ca.bradj.questown.jobs.special;

import ca.bradj.questown.InventoryFullStrategy;
import ca.bradj.questown.QT;
import ca.bradj.questown.integration.jobs.*;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.world.QTWorldAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.OptionalInt;

public class CompostAtWorkspotSpecialRule extends
        JobPhaseModifier {
    @Override
    public <X> @Nullable X beforeExtract(
            X context,
            BeforeExtractEvent<X> event
    ) {
        QTWorldAccess world = event.world();
        BlockPos spot = event.workSpot();

        OptionalInt processingLevel = world.getBlockIntProperty(spot, "level");
        if (processingLevel.isEmpty()) {
            QT.JOB_LOGGER.error("{} is not a composter. Special rule will fail: {}", spot, getClass().getName());
            return null;
        }

        OptionalInt maxLevel = world.getMaxBlockIntProperty(spot, "level");
        if (maxLevel.isPresent() && processingLevel.getAsInt() >= maxLevel.getAsInt()) {
            world.setBlockIntProperty(spot, "level", 0);
            MCHeldItem toGive = MCHeldItem.fromMCItemStack(Items.BONE_MEAL.getDefaultInstance());
            X out = event.entity().tryGiveItem(context, toGive, InventoryFullStrategy.DROP_ON_GROUND);
            world.playSound(spot, SoundEvents.COMPOSTER_EMPTY, SoundSource.BLOCKS);
            return out;
        }

        int newLevel = processingLevel.getAsInt() + 1;
        if (newLevel >= 7) {
            newLevel = 8;
        }
        world.setBlockIntProperty(spot, "level", newLevel);
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
