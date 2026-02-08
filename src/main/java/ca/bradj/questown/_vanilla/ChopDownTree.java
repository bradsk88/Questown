package ca.bradj.questown._vanilla;

import ca.bradj.questown.InventoryFullStrategy;
import ca.bradj.questown.integration.jobs.BeforeExtractEvent;
import ca.bradj.questown.integration.jobs.BeforeTickEvent;
import ca.bradj.questown.integration.jobs.JobPhaseModifier;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.mobs.visitor.ItemAcceptor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class ChopDownTree extends JobPhaseModifier {

    @Override
    public <CONTEXT> @Nullable CONTEXT beforeExtract(
            CONTEXT ctxInput,
            BeforeExtractEvent<CONTEXT> event
    ) {
        CONTEXT context = super.beforeExtract(ctxInput, event);
        BlockPos treeTrunk = event.workSpot();
        ServerLevel level = event.world().asServerLevel();
        Block b = level.getBlockState(treeTrunk).getBlock();
        return removeBlock(context, level, treeTrunk, event.entity(), b);
    }

    private <CONTEXT> CONTEXT removeBlock(
            CONTEXT ctxInput,
            ServerLevel level,
            BlockPos treeTrunk,
            ItemAcceptor<CONTEXT> entity,
            Block block
    ) {
        for (BlockPos pos : BlockPos.betweenClosed(treeTrunk.offset(-1, 0, -1), treeTrunk.offset(1, 1, 1))) {
            BlockState bs = level.getBlockState(pos);
            if (bs.is(block)) {
                level.removeBlock(pos, true);
                MCHeldItem i = MCHeldItem.fromTown(block.asItem());
                ctxInput = entity.tryGiveItem(ctxInput, i, InventoryFullStrategy.REMOVE_FROM_WORLD);
                removeBlock(ctxInput, level, pos, entity, bs.getBlock());
            }
        }
        return ctxInput;
    }

    @Override
    public void beforeTick(BeforeTickEvent bxEvent) {
        super.beforeTick(bxEvent);
        // TODO: Clear leaves that are blocking the path to the trunk
    }
}
