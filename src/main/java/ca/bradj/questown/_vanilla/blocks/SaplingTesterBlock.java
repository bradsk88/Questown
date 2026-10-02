package ca.bradj.questown._vanilla.blocks;

import ca.bradj.questown.QT;
import ca.bradj.questown._vanilla.CheckTreePlantable;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.jobs.JobBlockTestContext;
import ca.bradj.questown.jobs.WorkLocation;
import ca.bradj.questown.world.MinecraftWorldAccess;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.CompatBlockColor;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class SaplingTesterBlock extends Block {
    public static final String ITEM_ID = "sapling_tester";

    public SaplingTesterBlock(
    ) {
        super(Compat.blockProps(CompatBlockColor.SNOW).strength(1.0F, 10.0F).noOcclusion());
    }

    @Override
    public InteractionResult useWithoutItem(
            BlockState p_60503_,
            Level level,
            BlockPos blockPos,
            Player player,
            BlockHitResult p_60508_
    ) {
        if (!(level instanceof ServerLevel sl)) {
            return InteractionResult.CONSUME;
        }

        ItemStack itemInHand = player.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND);
        if (!(itemInHand.getItem() instanceof BlockItem bi)) {
            return super.useWithoutItem(p_60503_, level, blockPos, player, p_60508_);
        }
        if (!(bi.getBlock() instanceof SaplingBlock)) {
            return super.useWithoutItem(p_60503_, level, blockPos, player, p_60508_);
        }
        boolean allowed = new CheckTreePlantable().postJobBlockCheckPassed(new JobBlockTestContext(
                new MinecraftWorldAccess(sl), new WorkLocation.BlockInfo() {
            @Override
            public BlockState state(BlockPos bp) {
                return level.getBlockState(bp);
            }

            @Override
            public @Nullable BlockEntity entity(BlockPos bp) {
                return level.getBlockEntity(bp);
            }
        }, blockPos, () -> ImmutableList.of(MCHeldItem.fromTown(itemInHand)), ImmutableList::of, false, false
        ));

        QT.BLOCK_LOGGER.info("Sapling tester at {}: {}", blockPos, allowed ? "allowed" : "not allowed");

        return InteractionResult.CONSUME;
    }

    @Override
    protected boolean isAir(BlockState state) {
        return true;
    }
}
