package ca.bradj.questown.items;

import net.minecraft.world.level.Level;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.InteractionResult;
import net.minecraft.server.level.ServerPlayer;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import ca.bradj.questown.core.init.BlocksInit;
import net.minecraft.world.item.BlockItem;


/**
 * The Block of Progress item. Every BOP is plain: a job change takes an open job-change quest's job
 * or a random root.
 */
public class BlockOfProgressItem extends BlockItem {

    public BlockOfProgressItem(Properties props) {
        super(BlocksInit.BLOCK_OF_PROGRESS.get(), props);
    }

    /**
     * A Block of Progress is not a building block: used on the flag it goes back into it (a sneak
     * skips the flag's own click handler), and anywhere else it does nothing, so it is never placed
     * on the ground and destroyed.
     */
    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        if (level.getBlockEntity(ctx.getClickedPos()) instanceof TownFlagBlockEntity flag) {
            if (ctx.getPlayer() instanceof ServerPlayer sp) {
                flag.returnBopFromPlayer(sp, ctx.getItemInHand());
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        return InteractionResult.PASS;
    }
}
