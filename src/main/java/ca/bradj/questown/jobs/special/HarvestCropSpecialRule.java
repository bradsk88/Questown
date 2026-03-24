package ca.bradj.questown.jobs.special;

import ca.bradj.questown.InventoryFullStrategy;
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
        BlockPos cropPos = findHarvestableCrop(world, event);
        if (cropPos == null) {
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

    private static <X> @Nullable BlockPos findHarvestableCrop(
            QTWorldAccess world,
            BeforeExtractEvent<X> event
    ) {
        BlockPos workSpot = event.workSpot();
        if (isFullyGrown(world, workSpot)) {
            return workSpot;
        }
        List<BlockPos> candidates = world.getShuffledCopy(event.jobBlockPositions().get());
        for (BlockPos candidate : candidates) {
            if (isFullyGrown(world, candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean isFullyGrown(QTWorldAccess world, BlockPos pos) {
        OptionalInt stage = world.getBlockIntProperty(pos, "age");
        if (stage.isEmpty()) {
            return false;
        }
        OptionalInt maxStage = world.getMaxBlockIntProperty(pos, "age");
        return maxStage.isPresent() && stage.getAsInt() == maxStage.getAsInt();
    }
}
