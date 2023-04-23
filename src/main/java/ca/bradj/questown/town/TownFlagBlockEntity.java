package ca.bradj.questown.town;

import ca.bradj.questown.Questown;
import ca.bradj.questown.core.init.TilesInit;
import ca.bradj.questown.logic.TownCycle;
import ca.bradj.roomrecipes.adapter.Positions;
import ca.bradj.roomrecipes.core.Room;
import ca.bradj.roomrecipes.core.space.Position;
import ca.bradj.roomrecipes.logic.DoorDetection;
import ca.bradj.roomrecipes.recipes.RecipeDetection;
import ca.bradj.roomrecipes.recipes.RoomRecipe;
import ca.bradj.roomrecipes.render.RoomEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;

public class TownFlagBlockEntity extends BlockEntity implements TownCycle.BlockChecker, TownCycle.DoorsListener, TownCycle.NewRoomHandler, TownCycle.RoomTicker, DoorDetection.DoorChecker {

    private static int radius = 20; // TODO: Move to config

    public static final String ID = "flag_base_block_entity";

    private final Collection<Position> doors = new ArrayList<>();

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

        TownCycle.roomsTick(Positions.FromBlockPos(e.getBlockPos()), e, e.doors, e, e, e);
    }

    private void putDoor(Position dp) {
        if (this.doors.contains(dp)) {
            return;
        }
        this.doors.add(dp);
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
    public void roomDestroyed(
            Position doorPos
    ) {
        state.unsetRoomAtDoorPos(doorPos);
        handleRoomDestroyed(doorPos);
    }

    @Override
    public void roomTick(
            Room room
    ) {
        // TODO: Explicitly handle nested and conjoined rooms

        if (!(level instanceof ServerLevel)) {
            return;
        }

        Optional<Room> detectedRoom = state.getDetectedRoom(room.getDoorPos());
        if (detectedRoom.isEmpty() || !detectedRoom.get().equals(room)) {
            handleRoomChange(room);
        }

        Optional<RoomRecipe> recipe = RecipeDetection.getActiveRecipe(level, room, this);
        Questown.LOGGER.debug("Current Recipe: " + recipe);

        handleRecipeUpdate(room, recipe);

        for (RoomRecipe quest : state.getQuests()) {
            if (state.hasRecipe(quest)) {
                handleQuestCompleted(quest);
            }
        }

    }

    private void handleRecipeUpdate(
            Room room,
            Optional<RoomRecipe> recipe
    ) {
        Position doorPos = room.getDoorPos();
        if (recipe.isEmpty() && state.roomDoorExistsAt(doorPos)) {
            handleRoomDestroyed(doorPos);
        }
        if (recipe.isPresent() && !state.roomDoorExistsAt(doorPos)) {
            state.setRecipeAtDoorPosition(doorPos, recipe.get());
            broadcastMessage(new TranslatableComponent(
                    "messages.building.room_created",
                    new TranslatableComponent("room." + recipe.get().getId().getPath()),
                    doorPos.getUIString()
            ));
        }
        if (recipe.isPresent() && state.roomDoorExistsAt(doorPos)) {
            Optional<RoomRecipe> currentRecipe = state.getRecipeAtDoorPos(doorPos);
            if (currentRecipe.isPresent() && !currentRecipe.get().equals(recipe.get())) {
                state.setRecipeAtDoorPosition(doorPos, recipe.get());
                broadcastMessage(new TranslatableComponent(
                        "messages.building.room_changed",
                        new TranslatableComponent("room." + currentRecipe.get().getId().getPath()),
                        new TranslatableComponent("room." + recipe.get().getId().getPath()),
                        doorPos.getUIString()
                ));
            }
        }
    }

    private void broadcastMessage(TranslatableComponent msg) {
        level.getServer().getPlayerList().broadcastMessage(msg, ChatType.GAME_INFO, null);
    }

    private void handleRoomChange(Room room) {
        state.setDetectedRoom(room);
        RoomEffects.renderParticlesBetween(room.getSpace(), (x, y, z) -> {
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

    private void handleQuestCompleted(RoomRecipe quest) {
        state.clearQuest(quest);
        broadcastMessage(new TranslatableComponent(
                "messages.town_flag.quest_completed",
                new TranslatableComponent("room." + quest.getId().getPath())
        ));
        FireworkRocketEntity firework = new FireworkRocketEntity(
                level,
                getBlockPos().getX(),
                getBlockPos().getY() + 10,
                getBlockPos().getZ(),
                Items.FIREWORK_ROCKET.getDefaultInstance()
        );
        level.addFreshEntity(firework);
    }

    private void handleRoomDestroyed(Position doorPos) {
        RoomRecipe roomRecipe = state.unsetRecipeAtDoorPos(doorPos);
        if (roomRecipe == null) {
            return;
        }
        broadcastMessage(new TranslatableComponent(
                "messages.building.room_destroyed",
                new TranslatableComponent("room." + roomRecipe.getId().getPath()),
                doorPos.getUIString()
        ));
    }

}
