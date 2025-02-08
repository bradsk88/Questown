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
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class DestroyBushSpecialRule extends
        JobPhaseModifier {
    @Override
    public <X> X beforeExtract(
            X context,
            BeforeExtractEvent<X> event
    ) {
        ServerLevel level = event.level();
        BlockPos pos = event.workSpot();
        BlockState bs = level.getBlockState(pos);
        if (!(bs.getBlock() instanceof BushBlock)) {
            QT.JOB_LOGGER.error("Block at {} is not a bush. Special rule failed to apply. [{}]", pos, bs);
            return null;
        }
        List<ItemStack> drops = BushBlock.getDrops(bs, level, pos, null);
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
        level.removeBlock(pos, true);
        Compat.playNeutralSound(level, pos, SoundEvents.GRASS_BREAK);
        return outContext;
    }
}
