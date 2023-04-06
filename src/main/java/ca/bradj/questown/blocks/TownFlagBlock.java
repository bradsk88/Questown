package ca.bradj.questown.blocks;

import ca.bradj.questown.core.init.ModItemGroup;
import ca.bradj.questown.core.init.TilesInit;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;


public class TownFlagBlock extends BaseEntityBlock {

    public static final String ITEM_ID = "town_flag_block";
    private Entity entity;
    public static final Item.Properties ITEM_PROPS = new Item.Properties().
            tab(ModItemGroup.QUESTOWN_GROUP);

    public TownFlagBlock() {
        super(
                BlockBehaviour.Properties.
                        of(Material.WEB).
                        strength(1f)
        );
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(
            BlockPos pos,
            BlockState state
    ) {
        this.entity = TilesInit.TOWN_FLAG.get().create(pos, state);
        return this.entity;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level,
            BlockState blockState,
            BlockEntityType<T> entityType
    ) {
        return level.isClientSide ? null : createTickerHelper(
                entityType, TilesInit.TOWN_FLAG.get(), Entity::tick
        );
    }

    @Override
    public InteractionResult use(
            BlockState p_60503_,
            Level level,
            BlockPos p_60505_,
            Player p_60506_,
            InteractionHand p_60507_,
            BlockHitResult p_60508_
    ) {
        // TODO: Declare town
        return InteractionResult.PASS;
    }

    public static class Entity extends BlockEntity {

        public static final String ID = "town_flag_block_entity";
        private Entity mainBlock;

        public Entity(
                BlockPos p_155229_,
                BlockState p_155230_
        ) {
            super(TilesInit.TOWN_FLAG.get(), p_155229_, p_155230_);
        }

        @Override
        public void deserializeNBT(CompoundTag nbt) {
            super.deserializeNBT(nbt);
        }

        public static void tick(
                Level level,
                BlockPos blockPos,
                BlockState state,
                Entity e
        ) {
            // TODO: Scan for doors to detect rooms
            // When a room is enclosed, trigger an event
            // TODO: Use config to determine search distance
        }
    }
}
