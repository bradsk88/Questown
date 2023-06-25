package ca.bradj.questown.blocks;

import ca.bradj.questown.integration.minecraft.GathererStatuses;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.level.material.MaterialColor;

public class GathererDummyBlock extends Block { // extends BaseEntityBlock {

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

    //    @Nullable
//    @Override
//    public BlockEntity newBlockEntity(
//            BlockPos pos,
//            BlockState state
//    ) {
//        return new TownFlagBlockEntity(pos, state);
//    }
//
//    @Nullable
//    @Override
//    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
//            Level level,
//            BlockState blockState,
//            BlockEntityType<T> entityType
//    ) {
//        return level.isClientSide ? null : createTickerHelper(
//                entityType, TilesInit.TOWN_FLAG.get(), TownFlagBlockEntity::tick
//        );
//    }

}
