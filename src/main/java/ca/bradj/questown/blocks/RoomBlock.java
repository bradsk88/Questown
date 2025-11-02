package ca.bradj.questown.blocks;

import ca.bradj.questown.Questown;
import ca.bradj.questown.blocks.entity.BlockAsRoomEntity;
import ca.bradj.questown.core.init.TilesInit;
import ca.bradj.questown.logic.RoomRecipes;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import ca.bradj.roomrecipes.recipes.RoomRecipe;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class RoomBlock extends TownFlagSubBlock<BlockAsRoomEntity> implements EntityBlock, Roomable {
    public RoomBlock(Properties p_49795_) {

        super(
                p_49795_,
                (bp, bs) -> TilesInit.BLOCK_AS_ROOM.get().create(bp, bs),
                BlockAsRoomEntity::tick
        );
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
            return stateForPlacement;
        }
        parent.getRoomHandle().registerBlockAsRoom(getRoomId(this), ctx.getClickedPos());
        return stateForPlacement;
    }

    public static @NotNull ResourceLocation getRoomId(RoomBlock rb) {
        // TODO: Handle WelcomeMatBlock
//        if (rb instanceof WelcomeMatBlock wmb) {
//            return SpecialQuests.TOWN_GATE;
//        }
        return Questown.ResourceLocation("block_room/" + rb.getDescriptionId());
    }

    @Override
    protected BlockEntityType<BlockAsRoomEntity> getTickerEntityType() {
        return TilesInit.BLOCK_AS_ROOM.get();
    }

    public RoomRecipe asRecipe() {
        Ingredient ing = Ingredient.of(asItem());
        return RoomRecipes.standard(
                getRoomId(this),
                NonNullList.withSize(1, ing)
        );
    }
}
