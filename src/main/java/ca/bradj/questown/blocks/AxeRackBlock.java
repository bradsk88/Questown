package ca.bradj.questown.blocks;

import ca.bradj.questown.blocks.entity.AxeRackBlockEntity;
import ca.bradj.questown.core.init.items.ItemsInit;
import ca.bradj.questown.core.network.QuestownNetwork;
import ca.bradj.questown.core.network.SyncBlockItemMessage;
import ca.bradj.questown.integration.minecraft.MCContainerInterface;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.Containers;
import ca.bradj.questown.jobs.declarative.MCExtra;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.level.material.MaterialColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

public class AxeRackBlock extends Block implements InsertedItemAware, EntityBlock {
    public static final String ITEM_ID = "axe_rack_block";

    public static final int MAX = 1;

    public AxeRackBlock(
    ) {
        super(Properties.of(Material.WOOD, MaterialColor.WOOD).strength(1.0F, 10.0F).noOcclusion());
        this.registerDefaultState(this.stateDefinition.any());
    }


    protected static final VoxelShape SHAPE = Block.box(4, 0.0D, 4, 12, 7, 12);

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
        return this.defaultBlockState();
    }

    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> p_51385_) {
        return;
    }


    @Override
    public void onRemove(
            BlockState p_51538_,
            Level p_51539_,
            BlockPos pos,
            BlockState p_51541_,
            boolean p_51542_
    ) {
        if (!p_51538_.is(p_51541_.getBlock())) {
            BlockEntity blockentity = p_51539_.getBlockEntity(pos);
            if (blockentity instanceof AxeRackBlockEntity fde) {
                if (p_51539_ instanceof ServerLevel sl) {
                    dropAllLoot(pos, fde, sl);
                }
                p_51539_.updateNeighbourForOutputSignal(pos, this);
            }

            super.onRemove(p_51538_, p_51539_, pos, p_51541_, p_51542_);
        }

    }

    private static void dropAllLoot(
            BlockPos pos,
            MCContainerInterface fde,
            ServerLevel sl
    ) {
        for (int i = 0; i < fde.size(); i++) {
            MCTownItem m = fde.getItem(i);
            if (m.isEmpty()) {
                continue;
            }
            sl.addFreshEntity(new ItemEntity(sl, pos.getX(), pos.getY(), pos.getZ(), m.toMCItemStack()));
        }
        ItemStack item = ItemsInit.AXE_RACK_BLOCK.get().getDefaultInstance();
        sl.addFreshEntity(new ItemEntity(sl, pos.getX(), pos.getY(), pos.getZ(), item));
    }

    @Override
    public void handleInsertedItem(
            MCExtra extra,
            BlockPos bp,
            MCHeldItem item
    ) {
        BlockEntity e = extra.town().getServerLevel().getBlockEntity(bp);
        if (!(e instanceof AxeRackBlockEntity rack)) {
            return;
        }
        rack.addItem(item);
    }

    @Override
    public InteractionResult use(
            BlockState p_60503_,
            Level level,
            BlockPos p_60505_,
            Player p_60506_,
            InteractionHand hand,
            BlockHitResult p_60508_
    ) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.CONSUME;
        }
        if (!(level instanceof ServerLevel sl)) {
            return InteractionResult.CONSUME;
        }

        BlockEntity entity = sl.getBlockEntity(p_60505_);
        if (!(entity instanceof AxeRackBlockEntity rack)) {
            return InteractionResult.CONSUME;
        }

        ItemStack itemInHand = p_60506_.getItemInHand(hand);
        if (itemInHand.isEmpty()) {
            return take(rack, p_60506_);
        }

        if (rack.isFull()) {
            return InteractionResult.CONSUME;
        }
        ItemStack copy = itemInHand.copy();
        int index = Containers.addIfPossible(itemInHand, rack);
        if (index >= 0) {
            QuestownNetwork.CHANNEL.send(
                    PacketDistributor.ALL.noArg(),
                    new SyncBlockItemMessage(p_60505_, copy, index)
            );
        }

        return InteractionResult.CONSUME;
    }

    private InteractionResult take(
            AxeRackBlockEntity entity,
            Player p_60506_
    ) {
        for (int i = 0; i < entity.size(); i++) {
            MCTownItem item = entity.getItem(i);
            if (item == null || item.isEmpty()) {
                continue;
            }
            ItemStack stack = item.toQTItemStack();
            p_60506_.setItemInHand(InteractionHand.MAIN_HAND, stack);

            entity.removeItem(i);
            QuestownNetwork.CHANNEL.send(
                    PacketDistributor.ALL.noArg(),
                    new SyncBlockItemMessage(entity.getBlockPos(), ItemStack.EMPTY, i)
            );
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(
            BlockPos blockPos,
            BlockState blockState
    ) {
        return new AxeRackBlockEntity(blockPos, blockState);
    }

    @Override
    public RenderShape getRenderShape(BlockState p_60550_) {
        return RenderShape.MODEL;
    }
}
