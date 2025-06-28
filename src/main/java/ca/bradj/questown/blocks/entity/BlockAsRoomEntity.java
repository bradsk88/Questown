package ca.bradj.questown.blocks.entity;

import ca.bradj.questown.blocks.*;
import ca.bradj.questown.core.init.TilesInit;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

public class BlockAsRoomEntity extends BlockEntity implements TownFlagSubEntity {

    public static final List<Supplier<RoomBlock>> ALL = new ArrayList<>();

    private final List<Runnable> tickListeners = new ArrayList<>();

    public BlockAsRoomEntity(
            BlockPos p_155229_,
            BlockState p_155230_
    ) {
        super(TilesInit.BLOCK_AS_ROOM.get(), p_155229_, p_155230_);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <B extends RoomBlock> RegistryObject<Block> register(RegistryObject<B> register) {
        ALL.add(register::get);
        return (RegistryObject) register;
    }

    @Override
    public Collection<ItemStack> dropWhenOrphaned(BlockPos flagPos) {
        ItemStack toDrop = Items.OAK_SIGN.getDefaultInstance();
        TownFlagBlock.StoreParentOnNBT(toDrop, flagPos);
        return ImmutableList.of(toDrop);
    }

    @Override
    public void addTickListener(Runnable listener) {
        this.tickListeners.add(listener);
    }

    public static void tick(
            Level level,
            BlockPos pos,
            BlockState blockState,
            BlockAsRoomEntity entity
    ) {
        entity.tickListeners.forEach(Runnable::run);
    }
}