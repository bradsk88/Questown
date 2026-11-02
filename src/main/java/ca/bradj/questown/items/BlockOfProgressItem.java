package ca.bradj.questown.items;

import net.minecraft.world.level.Level;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.InteractionResult;
import net.minecraft.server.level.ServerPlayer;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import ca.bradj.questown.core.init.BlocksInit;
import net.minecraft.world.item.BlockItem;


public class BlockOfProgressItem extends BlockItem {

    public BlockOfProgressItem(Properties props) {
        super(BlocksInit.BLOCK_OF_PROGRESS.get(), props);
    }

    // Used on the flag it goes back in; anywhere else it does nothing, so it is never placed.
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
