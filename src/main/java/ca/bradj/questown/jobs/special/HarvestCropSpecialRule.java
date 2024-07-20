package ca.bradj.questown.jobs.special;

import ca.bradj.questown.InventoryFullStrategy;
import ca.bradj.questown.QT;
import ca.bradj.questown.integration.jobs.BeforeExtractEvent;
import ca.bradj.questown.integration.jobs.JobPhaseModifier;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.mc.Compat;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class HarvestCropSpecialRule implements
        JobPhaseModifier {
    @Override
    public <X> X beforeExtract(X context, BeforeExtractEvent<X> event) {
        ServerLevel level = event.level();
        BlockPos cropBlock = event.workSpot();
        BlockState bs = level.getBlockState(cropBlock);
        if (!(bs.getBlock() instanceof CropBlock cb)) {
            QT.JOB_LOGGER.error("Block at {} is not a crop. Special rule failed to apply. [{}]", cropBlock, bs);
            return null;
        }
        if (!cb.isMaxAge(bs)) {
            QT.JOB_LOGGER.error("Crop block at {} is not full age. Special rule failed to apply. [{}]", cropBlock, bs);
            return null;
        }
        List<ItemStack> drops = CropBlock.getDrops(bs, level, cropBlock, null);
        X nextContext = context;
        X outContext = null;
        for (ItemStack i : drops) {
            @Nullable X o = event.entity().tryGiveItem(
                    nextContext,
                    MCHeldItem.fromMCItemStack(i),
                    InventoryFullStrategy.DROP_ON_GROUND
            );
            if (o != null) {
                nextContext = o;
                outContext = o;
            }
        }
        bs = bs.setValue(CropBlock.AGE, 0);
        level.setBlock(cropBlock, bs, 10);
        Compat.playNeutralSound(level, cropBlock, SoundEvents.CROP_BREAK);
        return outContext;
    }
}
