package ca.bradj.questown._vanilla;

import ca.bradj.questown.InventoryFullStrategy;
import ca.bradj.questown.integration.jobs.BeforeExtractEvent;
import ca.bradj.questown.integration.jobs.BeforeTickEvent;
import ca.bradj.questown.integration.jobs.JobPhaseModifier;
import ca.bradj.questown.integration.jobs.QTNativeRule;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ChopDownTree extends JobPhaseModifier implements QTNativeRule {

    @Override
    public <CONTEXT> @Nullable CONTEXT beforeExtract(
            CONTEXT ctxInput,
            BeforeExtractEvent<CONTEXT> event
    ) {
        List<ItemStack> drops = chopFirstTree(event);
        if (drops.isEmpty()) {
            return null;
        }
        CONTEXT context = ctxInput;
        CONTEXT outContext = null;
        for (ItemStack drop : drops) {
            CONTEXT o = event.entity().tryGiveItem(
                    context, MCHeldItem.fromTown(drop), InventoryFullStrategy.REMOVE_FROM_WORLD
            );
            if (o != null) {
                context = o;
                outContext = o;
            }
        }
        return outContext;
    }

    /** Chops the work spot's tree, or else the first log among the job's block positions. */
    private <CONTEXT> List<ItemStack> chopFirstTree(BeforeExtractEvent<CONTEXT> event) {
        List<ItemStack> atWorkSpot = event.world().chopTree(event.workSpot());
        if (!atWorkSpot.isEmpty()) {
            return atWorkSpot;
        }
        for (BlockPos candidate : event.jobBlockPositions().get()) {
            List<ItemStack> drops = event.world().chopTree(candidate);
            if (!drops.isEmpty()) {
                return drops;
            }
        }
        return List.of();
    }

    @Override
    public void beforeTick(BeforeTickEvent bxEvent) {
        super.beforeTick(bxEvent);
        // TODO: Clear leaves that are blocking the path to the trunk
    }
}
