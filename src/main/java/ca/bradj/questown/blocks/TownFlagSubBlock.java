package ca.bradj.questown.blocks;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.core.init.TilesInit;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public abstract class TownFlagSubBlock extends BaseEntityBlock {
    protected TownFlagSubBlock(Properties p_49224_) {
        super(p_49224_);
    }


    @Nullable
    @Override
    public BlockEntity newBlockEntity(
            BlockPos p_153215_,
            BlockState p_153216_
    ) {
        return new Entity(p_153215_, p_153216_);
    }


    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level,
            BlockState blockState,
            BlockEntityType<T> entityType
    ) {
        return level.isClientSide ? null : createTickerHelper(entityType, TilesInit.JOB_BOARD.get(), Entity::tick);
    }

    public static class Entity extends BlockEntity {

        private int ticksWithoutParent;
        private boolean everTickedByParent;

        public Entity(
                BlockPos p_155229_,
                BlockState p_155230_
        ) {
            super(TilesInit.JOB_BOARD.get(), p_155229_, p_155230_);
        }

        public void parentTick() {
            this.ticksWithoutParent = 0;
            this.everTickedByParent = true;
        }

        public static void tick(
                Level level,
                BlockPos pos,
                BlockState blockState,
                Entity entity
        ) {
            if (!(level instanceof ServerLevel sl)) {
                return;
            }
            if (!entity.everTickedByParent) {
                return;
            }
            if (entity.ticksWithoutParent++ > Config.FLAG_SUB_BLOCK_RETENTION_TICKS.get()) {
                QT.BLOCK_LOGGER.debug("Parent has stopped ticking. Job board removed at {}", pos);
                level.removeBlock(pos, true);
                sl.addFreshEntity(new ItemEntity(sl,
                        pos.getX(),
                        pos.getY(),
                        pos.getZ(),
                        Items.OAK_SIGN.getDefaultInstance()
                ));
            }
        }
    }
}
