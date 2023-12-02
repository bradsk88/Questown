package ca.bradj.questown.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiFunction;

public abstract class TownFlagSubBlock<I extends BlockEntity & TownFlagSubEntity> extends BaseEntityBlock {
    private final BiFunction<BlockPos, BlockState, I> entityFactory;
    private final BlockEntityTicker<? super I> ticker;

    protected TownFlagSubBlock(
            Properties p_49224_,
            BiFunction<BlockPos, BlockState, I> entityFactory,
            BlockEntityTicker<? super I> ticker
    ) {
        super(p_49224_);
        this.entityFactory = entityFactory;
        this.ticker = ticker;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level,
            BlockState blockState,
            BlockEntityType<T> entityType
    ) {
        return level.isClientSide ? null : createTickerHelper(entityType, getTickerEntityType(), ticker);
    }

    protected abstract BlockEntityType<I> getTickerEntityType();

    @Nullable
    @Override
    public BlockEntity newBlockEntity(
            BlockPos p_153215_,
            BlockState p_153216_
    ) {
        return entityFactory.apply(p_153215_, p_153216_);
    }
}
