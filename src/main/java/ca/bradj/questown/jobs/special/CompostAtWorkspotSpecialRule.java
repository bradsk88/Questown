package ca.bradj.questown.jobs.special;

import ca.bradj.questown.InventoryFullStrategy;
import ca.bradj.questown.QT;
import ca.bradj.questown.integration.jobs.*;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.mc.Compat;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class CompostAtWorkspotSpecialRule extends
        JobPhaseModifier {
    @Override
    public <X> @Nullable X beforeExtract(
            X context,
            BeforeExtractEvent<X> event
    ) {
        BlockPos spot = event.workSpot();
        ServerLevel level = event.level();

        BlockState bs = level.getBlockState(spot);

        if (!(bs.getBlock() instanceof ComposterBlock)) {
            QT.JOB_LOGGER.error("{} is not a composter. Special rule will fail: {}", spot, getClass().getName());
            return null;
        }

        if (bs.getValue(ComposterBlock.LEVEL) == 8) {
            MCHeldItem toGive = MCHeldItem.fromMCItemStack(Items.BONE_MEAL.getDefaultInstance());
            X out = event.entity().tryGiveItem(context, toGive, InventoryFullStrategy.DROP_ON_GROUND);
            bs = bs.setValue(ComposterBlock.LEVEL, 0);
            level.setBlockAndUpdate(spot, bs);
            Compat.playSound(event.level(), spot, SoundEvents.COMPOSTER_EMPTY, SoundSource.BLOCKS);
            return out;
        }

        ItemStack stack = event.lastInsertedItem().getDefaultInstance();
        BlockState blockstate = ComposterBlock.insertItem(bs, level, stack, spot);
        if (stack.getCount() > 0) {
            // didn't insert successfully
            return null;
        }

        level.setBlockAndUpdate(spot, blockstate);

        return context;
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

    @Override
    public void beforeTick(BeforeTickEvent bxEvent) {

    }
}
