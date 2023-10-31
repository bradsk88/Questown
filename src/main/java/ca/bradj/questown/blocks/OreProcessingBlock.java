package ca.bradj.questown.blocks;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.core.init.items.ItemsInit;
import ca.bradj.questown.jobs.Jobs;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.level.material.MaterialColor;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Random;

import static ca.bradj.questown.blocks.JobBlock.*;

public class OreProcessingBlock extends HorizontalDirectionalBlock implements ItemAccepting {
    public static final String ITEM_ID = "ore_processing_block";

    private static final int BAKE_STATE_EMPTY = 0;
    private static final int BAKE_STATE_FILLED = 1;
    private static final int BAKE_STATE_HAS_ORE = 2;

    public OreProcessingBlock(
    ) {
        super(
                Properties
                        .of(Material.WOOL, MaterialColor.COLOR_BROWN)
                        .strength(1.0F, 10.0F)
        );
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(PROCESSING_STATE, 0)
                .setValue(FACING, Direction.NORTH)
                .setValue(WORK_LEFT, 0)
        );
    }

    @Override
    public BlockState insertItem(
            ServerLevel level,
            BlockPos bp,
            ItemStack item,
            int workToNextStep
    ) {
        BlockState oldState = level.getBlockState(bp);

        // FIXME: Check item type and state
        int curValue = oldState.getValue(PROCESSING_STATE);
        if (
                canAcceptOre(level, bp) && item.is(Items.IRON_ORE) // TODO: Support more ores
        ) {
            item.shrink(1);
            int val = curValue + 1;
            BlockState blockState = setProcessingState(oldState, val);
            if (val == BAKE_STATE_FILLED) {
                blockState = blockState.setValue(WORK_LEFT, Config.SMELTER_WORK_REQUIRED.get());
            }
            level.setBlockAndUpdate(bp, blockState);
            return blockState;
        }
        return oldState;
    }

    public static boolean canAcceptOre(ServerLevel sl, BlockPos bp) {
        BlockState oldState = sl.getBlockState(bp);
        if (!oldState.hasProperty(PROCESSING_STATE)) {
            return false;
        }
        return oldState.getValue(PROCESSING_STATE) < BAKE_STATE_FILLED;
    }

    public static boolean canAcceptWork(ServerLevel sl, BlockPos bp) {
        BlockState oldState = sl.getBlockState(bp);
        if (!oldState.hasProperty(PROCESSING_STATE)) {
            return false;
        }
        return oldState.getValue(PROCESSING_STATE) == BAKE_STATE_FILLED;
    }

    public static boolean hasOreToCollect(
            ServerLevel sl,
            BlockPos bp) {
        BlockState oldState = sl.getBlockState(bp);
        if (!oldState.hasProperty(PROCESSING_STATE)) {
            return false;
        }
        return oldState.getValue(PROCESSING_STATE) == BAKE_STATE_HAS_ORE;
    }

    private static BlockState setProcessingState(
            BlockState oldState,
            int s
    ) {
        BlockState newState = oldState.setValue(PROCESSING_STATE, s);
        QT.BLOCK_LOGGER.debug("Processing state set to {}", s);
        return newState;
    }

    private static void moveOreToWorld(
            ServerLevel level,
            BlockPos b,
            @Nullable TakeFn takeFn
    ) {
        ItemStack is = new ItemStack(Items.RAW_IRON, 2);
        Jobs.getOrCreateItemFromBlock(level, b, takeFn, is);
        level.setBlock(b, level.getBlockState(b).setValue(PROCESSING_STATE, BAKE_STATE_EMPTY), 11);
    }

    @Override
    public List<ItemStack> getDrops(
            BlockState p_60537_,
            LootContext.Builder p_60538_
    ) {
        // FIXME: Also drop stuff inside
        return ImmutableList.of(ItemsInit.ORE_PROCESSING_BLOCK.get().getDefaultInstance());
    }

    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState blockState = this.defaultBlockState().setValue(PROCESSING_STATE, 0);
        if (!(ctx.getLevel() instanceof ServerLevel sl)) {
            return blockState;
        }
        return blockState
                .setValue(FACING, ctx.getHorizontalDirection().getOpposite())
                .setValue(WORK_LEFT, 0);
    }

    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> p_51385_) {
        JobBlock.defaultBlockStateDefinition(p_51385_);
    }


    public void animateTick(
            BlockState p_53635_,
            Level p_53636_,
            BlockPos p_53637_,
            Random p_53638_
    ) {
        if (p_53635_.getValue(PROCESSING_STATE) == BAKE_STATE_FILLED) {
            double d0 = (double) p_53637_.getX() + 0.5D;
            double d1 = (double) p_53637_.getY() + 1;
            double d2 = (double) p_53637_.getZ() + 0.5D;
            if (p_53638_.nextDouble() < 0.1D) {
                p_53636_.playSound(
                        null,
                        d0,
                        d1,
                        d2,
                        SoundEvents.VILLAGER_WORK_TOOLSMITH,
                        SoundSource.BLOCKS,
                        1.0F,
                        1.0F
                );
            }

            Direction direction = p_53635_.getValue(FACING);
            Direction.Axis direction$axis = direction.getAxis();
            double d3 = 0.52D;
            double d4 = p_53638_.nextDouble() * 0.6D - 0.3D;
            double d5 = direction$axis == Direction.Axis.X ? (double) direction.getStepX() * 0.52D : d4;
            double d6 = p_53638_.nextDouble() * 6.0D / 16.0D;
            double d7 = direction$axis == Direction.Axis.Z ? (double) direction.getStepZ() * 0.52D : d4;
            p_53636_.addParticle(ParticleTypes.WHITE_ASH, d0 + d5, d1 + d6, d2 + d7, 0.0D, 0.0D, 0.0D);
            p_53636_.addParticle(ParticleTypes.SMOKE, d0 + d5, d1 + d6, d2 + d7, 0.0D, 0.0D, 0.0D);
        }
    }

    @Override
    public InteractionResult use(
            BlockState blockState,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand p_60507_,
            BlockHitResult p_60508_
    ) {
        if (!(level instanceof ServerLevel sl)) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (hasOreToCollect(sl, pos)) {
            moveOreToWorld(sl, pos, is -> player.getInventory().add(is));
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (canAcceptOre(sl, pos)) {
            player.sendMessage(
                    new TranslatableComponent("message.smelter.villagers_will_add_ore"),
                    player.getUUID()
            );
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (canAcceptWork(sl, pos)) {
            player.sendMessage(
                    new TranslatableComponent("message.smelter.villagers_will_process"),
                    player.getUUID()
            );
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        player.sendMessage(
                new TranslatableComponent("message.smelter.processing"),
                player.getUUID()
        );
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
