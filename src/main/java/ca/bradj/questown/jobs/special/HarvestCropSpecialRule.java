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
import java.util.OptionalInt;

public class HarvestCropSpecialRule extends
        JobPhaseModifier {
    @Override
    public <X> X beforeExtract(
            X context,
            BeforeExtractEvent<X> event
    ) {
        QTWorldAccess world = event.world();
        BlockPos cropPos = event.workSpot();
        OptionalInt stage = world.getBlockIntProperty(cropPos, "age");
        if (stage.isEmpty()) {
            QT.JOB_LOGGER.error("Block at {} is not a crop. Special rule failed to apply.", cropPos);
            return null;
        }
        OptionalInt maxStage = world.getMaxBlockIntProperty(cropPos, "age");
        if (stage.getAsInt() != maxStage.getAsInt()) {
            QT.JOB_LOGGER.error("Crop block at {} is not full age. Special rule failed to apply.", cropPos);
            return null;
        }
        List<ItemStack> drops = world.getBlockDrops(cropPos, null);
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
        world.setBlockIntProperty(cropPos, "age", 0);
        world.playSound(cropPos, SoundEvents.CROP_BREAK);
        return outContext;
    }
}
