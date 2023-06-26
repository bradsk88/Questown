package ca.bradj.questown.blocks;

import ca.bradj.questown.core.init.TilesInit;
import ca.bradj.questown.integration.minecraft.GathererStatuses;
import ca.bradj.questown.jobs.GathererEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.level.material.MaterialColor;

import javax.annotation.Nullable;

public class GathererDummyBlock extends BaseEntityBlock {

    public static final String ITEM_ID = "gatherer_dummy";

    public static final EnumProperty<GathererStatuses> STATUS = EnumProperty.create(
            "status", GathererStatuses.class
    );

    public GathererDummyBlock() {
        super(
                Properties
                        .of(Material.STONE, MaterialColor.COLOR_GRAY)
                        .strength(1)
        );
        this.registerDefaultState(
                this.stateDefinition.any()
                        .setValue(STATUS, GathererStatuses.IDLE)
        );
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> p_49915_) {
        super.createBlockStateDefinition(p_49915_.add(STATUS));
    }

    @Override
    public RenderShape getRenderShape(BlockState blockState) {
        return RenderShape.MODEL;
    }

        @Nullable
    @Override
    public BlockEntity newBlockEntity(
            BlockPos pos,
            BlockState state
    ) {
            GathererEntity gathererEntity = new GathererEntity(pos, state);
            gathererEntity.initializeStatus(state);
            return gathererEntity;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level,
            BlockState blockState,
            BlockEntityType<T> entityType
    ) {
        return level.isClientSide ? null : createTickerHelper(
                entityType, TilesInit.GATHERER.get(), GathererEntity::tick
        );
    }

}
