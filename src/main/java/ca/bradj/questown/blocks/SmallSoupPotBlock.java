package ca.bradj.questown.blocks;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.init.items.ItemsInit;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.jobs.declarative.MCExtra;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.Util;
import ca.bradj.questown.town.workstatus.State;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.level.material.MaterialColor;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;
import java.util.function.Consumer;

public class SmallSoupPotBlock extends Block implements InsertedItemAware, StatefulJobBlock {
    public static final String ITEM_ID = "soup_pot_small";

    protected static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 6.0D, 16.0D);

    private static final IntegerProperty LEVEL = IntegerProperty.create(
            "level", 0, 1
    );

    public SmallSoupPotBlock(
    ) {
        super(
                Properties
                        .of(Material.GLASS, MaterialColor.TERRACOTTA_BROWN)
                        .strength(1.0F, 10.0F)
                        .noOcclusion()
        );
        this.registerDefaultState(this.stateDefinition.any()
                                                      .setValue(LEVEL, 0)
        );
    }

    @Override
    public VoxelShape getShape(
            BlockState p_60555_,
            BlockGetter p_60556_,
            BlockPos p_60557_,
            CollisionContext p_60558_
    ) {
        return SHAPE;
    }

    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return this.defaultBlockState()
                   .setValue(LEVEL, 0);
    }

    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> p_51385_) {
        p_51385_.add(LEVEL);
    }

    @Override
    public List<ItemStack> getDrops(
            BlockState p_60537_,
            LootContext.Builder p_60538_
    ) {
        // TODO: Preserve level
        return ImmutableList.of(
                ItemsInit.SMALL_SOUP_POT_BLOCK.get().getDefaultInstance()
        );
    }

    @Override
    public void handleInsertedItem(MCExtra extra, BlockPos bp, MCHeldItem item) {
        // TODO: Implement?
    }

    @Override
    public InteractionResult use(
            BlockState p_60503_,
            Level level,
            BlockPos blockPos,
            Player player,
            InteractionHand hand,
            BlockHitResult p_60508_
    ) {
        if (level.isClientSide()) {
            return InteractionResult.CONSUME;
        }

        return tryTakeSoup(level, blockPos, LEVEL, 1, player, hand);
    }

    public static InteractionResult tryTakeSoup(
            Level level,
            BlockPos p_60505_,
            IntegerProperty prop,
            int takeAmount,
            Player player,
            InteractionHand hand
    ) {
        BlockState p = level.getBlockState(p_60505_);
        ItemStack itemInHand = player.getItemInHand(hand);
        if (itemInHand.getItem() instanceof BlockItem) {
            return InteractionResult.PASS;
        }

        int oldVal = p.getValue(prop);
        if (oldVal < takeAmount) {
            Compat.sendMessage((ServerPlayer) player, Compat.translatable("message.questown.soup_pot.empty"));
            return InteractionResult.CONSUME;
        }

        if (!itemInHand.is(Items.BOWL)) {
            Compat.sendMessage((ServerPlayer) player, Compat.translatable("message.questown.soup_pot.need_bowl"));
            return InteractionResult.CONSUME;
        }


        int levelValue = Math.max((p.getValue(prop) - takeAmount), 0);
        p = p.setValue(prop, levelValue);
        level.setBlockAndUpdate(p_60505_, p);
        QT.BLOCK_LOGGER.debug("New level {}", levelValue);
        player.setItemInHand(hand, Items.MUSHROOM_STEW.getDefaultInstance());
        return InteractionResult.CONSUME;
    }

    @Override
    public void setProcessingState(
            ServerLevel sl,
            BlockPos pp,
            State bs
    ) {
        Util.setProcessingStateOnProperty(sl, LEVEL, bs, pp);
    }
}
