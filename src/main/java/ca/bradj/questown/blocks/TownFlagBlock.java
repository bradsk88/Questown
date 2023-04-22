package ca.bradj.questown.blocks;

import ca.bradj.questown.Questown;
import ca.bradj.questown.core.init.ModItemGroup;
import ca.bradj.questown.core.init.TilesInit;
import ca.bradj.questown.logic.TownCycle;
import ca.bradj.roomrecipes.adapter.Positions;
import ca.bradj.roomrecipes.core.space.InclusiveSpace;
import ca.bradj.roomrecipes.core.space.Position;
import ca.bradj.roomrecipes.logic.RoomDetector;
import ca.bradj.roomrecipes.recipes.RecipesInit;
import ca.bradj.roomrecipes.recipes.RoomRecipe;
import ca.bradj.roomrecipes.render.RoomEffects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.*;


public class TownFlagBlock extends BaseEntityBlock {

    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    public static final String ITEM_ID = "town_flag_block";
    private Entity entity;
    public static final Item.Properties ITEM_PROPS = new Item.Properties().
            tab(ModItemGroup.QUESTOWN_GROUP);

    public TownFlagBlock() {
        super(BlockBehaviour.Properties.copy(Blocks.COBWEB));
        this.registerDefaultState(this.stateDefinition.any().setValue(HALF, DoubleBlockHalf.LOWER));
    }

    // TODO: Fix so both blocks get destroyed when one is destroyed

    public boolean canSurvive(BlockState p_52783_, LevelReader p_52784_, BlockPos p_52785_) {
        BlockPos blockpos = p_52785_.below();
        BlockState blockstate = p_52784_.getBlockState(blockpos);
        return p_52783_.getValue(HALF) == DoubleBlockHalf.LOWER ? blockstate.isFaceSturdy(p_52784_, blockpos, Direction.UP) : blockstate.is(this);
    }

    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> p_52803_) {
        p_52803_.add(HALF);
    }

    @Override
    public void setPlacedBy(
            Level p_49847_,
            BlockPos p_49848_,
            BlockState p_49849_,
            @Nullable LivingEntity p_49850_,
            ItemStack p_49851_
    ) {
        p_49847_.setBlock(p_49848_.above(), p_49849_.setValue(HALF, DoubleBlockHalf.UPPER), 3);
    }

    @Override
    public RenderShape getRenderShape(BlockState blockState) {
        if (blockState.getValue(HALF).equals(DoubleBlockHalf.UPPER)) {
            return RenderShape.INVISIBLE;
        }
        return RenderShape.MODEL;
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

    public static class Entity extends BlockEntity implements TownCycle.BlockChecker, TownCycle.DoorsListener, TownCycle.NewRoomHandler, TownCycle.RoomTicker {

        private static int radius = 20; // TODO: Move to config

        public static final String ID = "town_flag_block_entity";

        private final Map<Position, RoomDetector> doors = new HashMap<>();
        private final Map<Position, RoomRecipe> roomRecipes = new HashMap<>();

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
            if (level.isClientSide()) {
                return;
            }

            long gameTime = level.getGameTime();
            long l = gameTime % 10;
            if (l != 0) {
                return;
            }

            TownCycle.townTick(Positions.FromBlockPos(e.getBlockPos()), e, e.doors.values(), e, e, e);
        }

        private void putDoor(Position dp) {
            if (this.doors.containsKey(dp)) {
                return;
            }
            this.doors.put(dp, new RoomDetector(dp, 30)); // TODO: Const
        }

        @Override
        public boolean IsEmpty(Position dp) {
            BlockPos bp = Positions.ToBlock(dp);
            return level.isEmptyBlock(bp) || level.isEmptyBlock(bp.above());
        }

        @Override
        public boolean IsDoor(Position dp) {
            return level.getBlockState(Positions.ToBlock(dp)).getBlock() instanceof DoorBlock;
        }

        @Override
        public void DoorAdded(Position dp) {
            this.putDoor(dp);
        }

        @Override
        public void DoorRemoved(Position dp) {
            this.doors.remove(dp);
            handleRoomDestroyed(dp);
        }

        @Override
        public void newRoomDetected(InclusiveSpace space) {
            RoomEffects.renderParticlesBetween(space, (x, y, z) -> {
                BlockPos bp = new BlockPos(x, y, z);
                if (!(level instanceof ServerLevel)) {
                    return;
                }
                if (!level.isEmptyBlock(bp)) {
                    return;
                }
                ((ServerLevel) level).sendParticles(ParticleTypes.HAPPY_VILLAGER, x, y, z, 2, 0, 1, 0, 1);
                ((ServerLevel) level).sendParticles(ParticleTypes.HAPPY_VILLAGER, x, y + 1, z, 2, 0, 1, 0, 1);
            });
        }

        @Override
        public void roomDestroyed(
                Position doorPos,
                ImmutableSet<Position> space
        ) {
            handleRoomDestroyed(doorPos);
        }

        @Override
        public void roomTick(
                Position doorPos,
                InclusiveSpace inclusiveSpace
        ) {
            // TODO: Explicitly handle nested and conjoined rooms

            if (!(level instanceof ServerLevel)) {
                return;
            }
            List<Block> blocksInSpace = getBlocksBetweenCoords(
                    level,
                    Positions.ToBlock(inclusiveSpace.getCornerA()),
                    Positions.ToBlock(inclusiveSpace.getCornerB()).above() // TODO: Support taller rooms?
            );
            RecipeManager recipeManager = level.getRecipeManager();

            SimpleContainer inv = new SimpleContainer(blocksInSpace.size());
            for (int i = 0; i < blocksInSpace.size(); i++) {
                ItemStack stackInSlot = new ItemStack(blocksInSpace.get(i), 1);
                inv.setItem(i, stackInSlot);
            }

            List<RoomRecipe> recipes = recipeManager.getAllRecipesFor(RecipesInit.ROOM);
            recipes = Lists.reverse(ImmutableList.sortedCopyOf(recipes));
            Optional<RoomRecipe> recipe = recipes.stream().filter(r -> r.matches(inv, level)).findFirst();

            Questown.LOGGER.debug("Current Recipe: " + recipe);

            if (recipe.isEmpty() && roomRecipes.containsKey(doorPos)) {
                handleRoomDestroyed(doorPos);
            }
            if (recipe.isPresent() && !roomRecipes.containsKey(doorPos)) {
                roomRecipes.put(doorPos, recipe.get());
                level.getServer().getPlayerList().broadcastMessage(
                        new TranslatableComponent(
                                "messages.building.room_created",
                                new TranslatableComponent("room." + recipe.get().getId().getPath()),
                                doorPos.getUIString()
                        ),
                        ChatType.GAME_INFO, null
                );
            }
            if (recipe.isPresent() && roomRecipes.containsKey(doorPos)) {
                RoomRecipe currentRecipe = roomRecipes.get(doorPos);
                if (!currentRecipe.equals(recipe.get())) {
                    roomRecipes.put(doorPos, recipe.get());
                    level.getServer().getPlayerList().broadcastMessage(
                            new TranslatableComponent(
                                    "messages.building.room_changed",
                                    new TranslatableComponent("room." + currentRecipe.getId().getPath()),
                                    new TranslatableComponent("room." + recipe.get().getId().getPath()),
                                    doorPos.getUIString()
                            ),
                            ChatType.GAME_INFO, null
                    );
                }
            }
        }

        private void handleRoomDestroyed(Position doorPos) {
            RoomRecipe roomRecipe = roomRecipes.remove(doorPos);
            if (roomRecipe == null) {
                return;
            }
            level.getServer().getPlayerList().broadcastMessage(
                    new TranslatableComponent(
                            "messages.building.room_destroyed",
                            new TranslatableComponent("room." + roomRecipe.getId().getPath()),
                            doorPos.getUIString()
                    ),
                    ChatType.GAME_INFO, null
            );
        }

        public List<Block> getBlocksBetweenCoords(
                Level level,
                BlockPos pos1,
                BlockPos pos2
        ) {
            List<Block> blockList = new ArrayList<>();

            // Get the chunk containing the starting and ending coordinates
            int xMin = Math.min(pos1.getX(), pos2.getX());
            int xMax = Math.max(pos1.getX(), pos2.getX());
            int zMin = Math.min(pos1.getZ(), pos2.getZ());
            int zMax = Math.max(pos1.getZ(), pos2.getZ());
            int chunkXMin = xMin >> 4;
            int chunkXMax = xMax >> 4;
            int chunkZMin = zMin >> 4;
            int chunkZMax = zMax >> 4;
            for (int chunkX = chunkXMin; chunkX <= chunkXMax; chunkX++) {
                for (int chunkZ = chunkZMin; chunkZ <= chunkZMax; chunkZ++) {
                    // Iterate over all blocks in the chunk and add them to the list
                    int blockXMin = Math.max(xMin, chunkX << 4);
                    int blockXMax = Math.min(xMax, (chunkX << 4) + 15);
                    int blockZMin = Math.max(zMin, chunkZ << 4);
                    int blockZMax = Math.min(zMax, (chunkZ << 4) + 15);
                    for (int blockX = blockXMin; blockX <= blockXMax; blockX++) {
                        for (int blockZ = blockZMin; blockZ <= blockZMax; blockZ++) {
                            int yMin = Math.min(pos1.getY(), pos2.getY());
                            int yMax = Math.max(pos1.getY(), pos2.getY());
                            for (int blockY = yMin; blockY <= yMax; blockY++) {
                                BlockPos blockPos = new BlockPos(blockX, blockY, blockZ);
                                Block block = level.getBlockState(blockPos).getBlock();
                                blockList.add(block);
                            }
                        }
                    }
                }
            }
            return blockList;
        }
    }
}
