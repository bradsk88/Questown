package ca.bradj.questown.items;

import ca.bradj.questown.Questown;
import ca.bradj.questown.blocks.TownFlagBlock;
import ca.bradj.questown.core.init.BlocksInit;
import ca.bradj.questown.core.init.items.ItemsInit;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public class FalseWallItem extends Item {
    public static final String ITEM_ID = "false_door_block";

    public FalseWallItem() {
        super(Questown.DEFAULT_ITEM_PROPS);
    }

    @Override
    public InteractionResult useOn(net.minecraft.world.item.context.UseOnContext ctx) {
        return placeAndRegisterContainedItem(ctx);
    }

    @NotNull
    private static InteractionResult placeAndRegisterContainedItem(
            net.minecraft.world.item.context.UseOnContext ctx
    ) {
        if (ctx.getPlayer() == null) {
            return InteractionResult.FAIL;
        }
        if (ctx.getPlayer().isShiftKeyDown()) {
            ItemStack newStack = ItemsInit.FALSE_DOOR.get().getDefaultInstance();
            TownFlagBlock.CopyParentFromNBT(ctx.getItemInHand(), newStack);
            ctx.getPlayer().setItemInHand(ctx.getHand(), newStack);
            return InteractionResult.CONSUME;
        }
        if (ctx.getLevel() instanceof ServerLevel sl) {
            sl.setBlockAndUpdate(ctx.getClickedPos().above(), BlocksInit.FALSE_WALL_BLOCK.get().defaultBlockState());
            return InteractionResult.CONSUME;
        }
        return InteractionResult.FAIL;
    }

}
