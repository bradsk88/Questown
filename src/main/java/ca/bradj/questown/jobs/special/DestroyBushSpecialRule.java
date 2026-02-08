package ca.bradj.questown.jobs.special;

import ca.bradj.questown.InventoryFullStrategy;
import ca.bradj.questown.QT;
import ca.bradj.questown.integration.jobs.BeforeExtractEvent;
import ca.bradj.questown.integration.jobs.JobPhaseModifier;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.world.QTWorldAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class DestroyBushSpecialRule extends
        JobPhaseModifier {
    @Override
    public <X> X beforeExtract(
            X context,
            BeforeExtractEvent<X> event
    ) {
        QTWorldAccess world = event.world();
        BlockPos pos = event.workSpot();
        List<ItemStack> drops = world.getBlockDrops(pos, null);
        if (drops.isEmpty()) {
            QT.JOB_LOGGER.error("Block at {} produced no drops. Special rule failed to apply.", pos);
            return null;
        }
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
        world.removeBlock(pos);
        world.playSound(pos, SoundEvents.GRASS_BREAK);
        return outContext;
    }
}
