package ca.bradj.questown.blocks;

import ca.bradj.questown.core.init.BlocksInit;
import ca.bradj.questown.core.init.items.ItemsInit;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.level.material.MaterialColor;
import net.minecraft.world.level.storage.loot.LootContext;

import java.util.List;

public class BreadOvenBlock extends Block {
    public static final String ITEM_ID = "bred_oven_block";

    public static final IntegerProperty BAKE_STATE = IntegerProperty.create(
            "bake_state", 0, 3
    );

    public BreadOvenBlock(
    ) {
        super(
                Properties
                        .of(Material.WOOL, MaterialColor.COLOR_BROWN)
                        .strength(1.0F, 10.0F)
                        .noCollission()
        );
    }

    public static boolean canAddIngredients(BlockState blockState) {
        if (!blockState.getBlock().equals(BlocksInit.BREAD_OVEN_BLOCK.get())) {
            return false;
        }
        if (!blockState.hasProperty(BAKE_STATE)) {
            return false;
        }
        return blockState.getValue(BAKE_STATE) <= 1;
    }

    public static BlockState insertItem(
            BlockState oldState,
            ItemStack item
    ) {
        int curValue = oldState.getValue(BAKE_STATE);
        if (curValue <= 1) {
            item.shrink(1);
            return oldState.setValue(BAKE_STATE, curValue + 1);
        }
        return oldState;
    }

    @Override
    public List<ItemStack> getDrops(
            BlockState p_60537_,
            LootContext.Builder p_60538_
    ) {
        return ImmutableList.of(ItemsInit.BREAD_OVEN_BLOCK.get().getDefaultInstance());
    }

    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState blockState = this.defaultBlockState().setValue(BAKE_STATE, 0);
        if (!(ctx.getLevel() instanceof ServerLevel sl)) {
            return blockState;
        }
        return blockState;
    }

    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> p_51385_) {
        p_51385_.add(BAKE_STATE);
    }
}
