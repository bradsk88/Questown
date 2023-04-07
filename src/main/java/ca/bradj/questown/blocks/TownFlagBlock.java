package ca.bradj.questown.blocks;

import ca.bradj.questown.core.init.ModItemGroup;
import ca.bradj.questown.core.init.TilesInit;
import ca.bradj.questown.logic.DoorFinder;
import ca.bradj.questown.logic.RoomDetector;
import ca.bradj.questown.rooms.DoorPos;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;


public class TownFlagBlock extends BaseEntityBlock {

    public static final String ITEM_ID = "town_flag_block";
    private Entity entity;
    public static final Item.Properties ITEM_PROPS = new Item.Properties().
            tab(ModItemGroup.QUESTOWN_GROUP);

    public TownFlagBlock() {
        super(
                BlockBehaviour.Properties.
                        of(Material.WEB).
                        strength(1f)
        );
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(
            BlockPos pos,
            BlockState state
    ) {
        this.entity = TilesInit.TOWN_FLAG.get().create(pos, state);
        return this.entity;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level,
            BlockState blockState,
            BlockEntityType<T> entityType
    ) {
        return level.isClientSide ? null : createTickerHelper(
                entityType, TilesInit.TOWN_FLAG.get(), Entity::tick
        );
    }

    @Override
    public InteractionResult use(
            BlockState p_60503_,
            Level level,
            BlockPos p_60505_,
            Player p_60506_,
            InteractionHand p_60507_,
            BlockHitResult p_60508_
    ) {
        // TODO: Declare town
        return InteractionResult.PASS;
    }

    public static class Entity extends BlockEntity {

        private static int radius = 20; // TODO: Move to config

        public static final String ID = "town_flag_block_entity";

        private final Map<DoorPos, RoomDetector> doors = new HashMap();

        public Entity(
                BlockPos p_155229_,
                BlockState p_155230_
        ) {
            super(TilesInit.TOWN_FLAG.get(), p_155229_, p_155230_);
        }

        @Override
        public void deserializeNBT(CompoundTag nbt) {
            super.deserializeNBT(nbt);
        }

        public static void tick(
                Level level,
                BlockPos blockPos,
                BlockState state,
                Entity e
        ) {
            findDoors(level, blockPos, e);
            for (RoomDetector rd : e.doors.values()) {
//                rd.update(level) TODO: This
            }
            // When a room is enclosed, trigger an event
        }

        private static void findDoors(
                Level level,
                BlockPos blockPos,
                Entity e
        ) {
            if (level.getGameTime() % 1000 != 0) {
                return;
            }
            Collection<DoorPos> doors = DoorFinder.LocateDoorsAroundPosition(
                    new DoorPos(blockPos.getX(), blockPos.getY(), blockPos.getZ()),
                    (DoorPos dp) -> {
                        BlockPos bp = new BlockPos(dp.x, dp.y, dp.z);
                        if (level.isEmptyBlock(bp)) {
                            return false;
                        }
                        return level.getBlockState(bp).getBlock() instanceof DoorBlock;
                    },
                    radius
            );
            doors.forEach(dp -> {
                e.putDoor(dp, level.getBlockEntity(new BlockPos(dp.x, dp.y, dp.z)));
            });
        }

        private void putDoor(
                DoorPos dp,
                BlockEntity blockEntity
        ) {
            this.doors.put(dp, new RoomDetector(dp, 5));
        }
    }
}
