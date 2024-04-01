package ca.bradj.questown.items;

import ca.bradj.questown.Questown;
import ca.bradj.questown.blocks.TownFlagBlock;
import ca.bradj.questown.town.TownFlagBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jetbrains.annotations.NotNull;

public class TownDoorTestItem extends Item {
    public static final String ITEM_ID = "town_door_tester";

    public TownDoorTestItem() {
        super(Questown.DEFAULT_ITEM_PROPS);
    }

    @Override
    public InteractionResult useOn(net.minecraft.world.item.context.UseOnContext ctx) {
        return startDebug(ctx);
    }

    @NotNull
    private static InteractionResult startDebug(net.minecraft.world.item.context.UseOnContext ctx) {
        if (ctx.getLevel()
               .isClientSide()) {
            return InteractionResult.PASS;
        }
        TownFlagBlockEntity parent = TownFlagBlock.GetParentFromNBT((ServerLevel) ctx.getLevel(), ctx.getItemInHand());
        BlockPos clickedPos = ctx.getClickedPos();
        BlockState bs = parent.getServerLevel()
                              .getBlockState(clickedPos);
        if (bs.getBlock() instanceof DoorBlock) {
            if (DoubleBlockHalf.UPPER.equals(bs.getValue(DoorBlock.HALF))) {
                clickedPos = clickedPos.below();
            }
        } else {
            bs = parent.getServerLevel()
                       .getBlockState(clickedPos.above());
            if (bs.getBlock() instanceof DoorBlock) {
                clickedPos = clickedPos.above();
            }
        }
        parent.getDebugHandle().startDebugTask(parent.getRoomHandle()
                                    .getDebugTaskForDoor(clickedPos));
        return InteractionResult.CONSUME;
    }

}
