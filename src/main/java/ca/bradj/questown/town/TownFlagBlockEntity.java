package ca.bradj.questown.town;

import ca.bradj.questown.Questown;
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
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityEvent;

import java.util.*;

public class TownFlagBlockEntity extends BlockEntity implements TownCycle.BlockChecker, TownCycle.DoorsListener, TownCycle.NewRoomHandler, TownCycle.RoomTicker {

    private static int radius = 20; // TODO: Move to config

    public static final String ID = "flag_base_block_entity";

    private final Map<Position, RoomDetector> doors = new HashMap<>();

    private final TownState state = new TownState();

    public TownFlagBlockEntity(
            BlockPos p_155229_,
            BlockState p_155230_
    ) {
        super(TilesInit.TOWN_FLAG.get(), p_155229_, p_155230_);
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        super.deserializeNBT(nbt);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level.isClientSide()) {
            return;
        }
        MinecraftForge.EVENT_BUS.addListener((EntityEvent.EnteringSection event) -> {
            if (event.getEntity() instanceof Player) {
                double v = event.getEntity().distanceToSqr(
                        this.worldPosition.getX() + 0.5D,
                        this.worldPosition.getY() + 0.5D,
                        this.worldPosition.getZ() + 0.5D
                );
                Questown.LOGGER.debug("Player detected at distance: " + v);
                if (v < 113) {
                    Player player = (Player) event.getEntity();
                    player.sendMessage(new TranslatableComponent("messages.town_flag.click_to_view_quests"), null);
                }
            }
        });

        state.tryInitialize(level.getRecipeManager());
    }

    public static void tick(
            Level level,
            BlockPos blockPos,
            BlockState state,
            TownFlagBlockEntity e
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

        if (recipe.isEmpty() && state.roomDoorExistsAt(doorPos)) {
            handleRoomDestroyed(doorPos);
        }
        if (recipe.isPresent() && !state.roomDoorExistsAt(doorPos)) {
            state.setRoomAtDoorPosition(doorPos, recipe.get());
            level.getServer().getPlayerList().broadcastMessage(
                    new TranslatableComponent(
                            "messages.building.room_created",
                            new TranslatableComponent("room." + recipe.get().getId().getPath()),
                            doorPos.getUIString()
                    ),
                    ChatType.GAME_INFO, null
            );
        }
        if (recipe.isPresent() && state.roomDoorExistsAt(doorPos)) {
            RoomRecipe currentRecipe = state.getRoomAtDoorPos(doorPos);
            if (!currentRecipe.equals(recipe.get())) {
                state.setRoomAtDoorPosition(doorPos, recipe.get());
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

        for (RoomRecipe quest : state.getQuests()) {
            if (state.hasRecipe(quest)) {
                handleQuestCompleted(quest);
            }
        }

    }

    private void handleQuestCompleted(RoomRecipe quest) {
        state.clearQuest(quest);
        level.getServer().getPlayerList().broadcastMessage(
                new TranslatableComponent(
                        "messages.town_flag.quest_completed",
                        new TranslatableComponent("room." + quest.getId().getPath())
                ),
                ChatType.GAME_INFO, null
        );
        FireworkRocketEntity firework = new FireworkRocketEntity(
                level,
                getBlockPos().getX(),
                getBlockPos().above().above().above().above().above().above().above().getY(),
                getBlockPos().getZ(),
                Items.FIREWORK_ROCKET.getDefaultInstance()
        );
        level.addFreshEntity(firework);
    }

    private void handleRoomDestroyed(Position doorPos) {
        RoomRecipe roomRecipe = state.unsetRoomAtDoorPos(doorPos);
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
