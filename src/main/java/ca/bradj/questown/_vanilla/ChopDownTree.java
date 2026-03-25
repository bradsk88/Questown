package ca.bradj.questown._vanilla;

import ca.bradj.questown.InventoryFullStrategy;
import ca.bradj.questown.integration.jobs.BeforeExtractEvent;
import ca.bradj.questown.integration.jobs.BeforeTickEvent;
import ca.bradj.questown.integration.jobs.JobPhaseModifier;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class ChopDownTree extends JobPhaseModifier {

    @Override
    public <CONTEXT> @Nullable CONTEXT beforeExtract(
            CONTEXT ctxInput,
            BeforeExtractEvent<CONTEXT> event
    ) {
        CONTEXT context = super.beforeExtract(ctxInput, event);
        BlockPos treeTrunk = event.workSpot();
        for (ItemStack drop : event.world().chopTree(treeTrunk)) {
            context = event.entity().tryGiveItem(context, MCHeldItem.fromTown(drop), InventoryFullStrategy.REMOVE_FROM_WORLD);
        }
        return context;
    }

    @Override
    public void beforeTick(BeforeTickEvent bxEvent) {
        super.beforeTick(bxEvent);
        // TODO: Clear leaves that are blocking the path to the trunk
    }
}
