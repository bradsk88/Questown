package ca.bradj.questown.blocks;

import ca.bradj.questown.QT;
import ca.bradj.questown.Questown;
import ca.bradj.questown.town.TownFlagBlockEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class RoomBlock extends Block implements EntityBlock, Roomable {
    public RoomBlock(Properties p_49795_) {
        super(p_49795_);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState stateForPlacement = super.getStateForPlacement(ctx);
        if (!(ctx.getLevel() instanceof ServerLevel sl)) {
            return stateForPlacement;
        }
        ItemStack item = ctx.getItemInHand();
        @Nullable TownFlagBlockEntity parent = TownFlagBlock.GetParentFromNBT(sl, item);
        if (parent == null) {
            QT.BLOCK_LOGGER.error("Failed to link room block to a flag. This is a bug, please report it.");
            return stateForPlacement;
        }
        parent.getRoomHandle().registerBlockAsRoom(getRoomId(this), ctx.getClickedPos());
        return stateForPlacement;
    }

    public static @NotNull ResourceLocation getRoomId(RoomBlock rb) {
        return Questown.ResourceLocation("block_room/" + rb.getDescriptionId());
    }
}
