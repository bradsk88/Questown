package ca.bradj.questown.town;

import ca.bradj.questown.logic.RoomRecipes;
import ca.bradj.questown.logic.TownCycle;
import ca.bradj.roomrecipes.adapter.Positions;
import ca.bradj.roomrecipes.core.Room;
import ca.bradj.roomrecipes.core.space.InclusiveSpace;
import ca.bradj.roomrecipes.core.space.Position;
import ca.bradj.roomrecipes.logic.DoorDetection;
import ca.bradj.roomrecipes.recipes.RecipeDetection;
import ca.bradj.roomrecipes.recipes.RoomRecipe;
import ca.bradj.roomrecipes.render.RoomEffects;
import ca.bradj.roomrecipes.rooms.ActiveRooms;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collection;
import java.util.Optional;

public class TownRooms implements TownCycle.BlockChecker, DoorDetection.DoorChecker, ActiveRooms.ChangeListener {

    private final ActiveRooms rooms = new ActiveRooms();
    private final int scanLevel;
    private final TownFlagBlockEntity entity;

    public TownRooms(
            int scanLevel,
            TownFlagBlockEntity entity
    ) {
        this.entity = entity;
        this.scanLevel = scanLevel;
        rooms.addChangeListener(this);
    }

    int getY() {
        return entity.getBlockPos().getY() + (scanLevel * 2);
    }

    @Override
    public boolean IsEmpty(Position dp) {
        BlockPos bp = Positions.ToBlock(dp, getY());
        BlockPos abp = bp.above();
        boolean empty = entity.getLevel().isEmptyBlock(bp);
        boolean emptyAbove = entity.getLevel().isEmptyBlock(abp);
        return empty || emptyAbove;
    }

    @Override
    public boolean IsWall(Position dp) {
        BlockPos bp = Positions.ToBlock(dp, getY());
        BlockPos abp = bp.above();
        if (this.IsEmpty(dp)) {
            return false;
        }
        Level level = entity.getLevel();
        BlockState blockState = level.getBlockState(bp);
        BlockState aboveBlockState = level.getBlockState(abp);
        if (isSolid(bp, level, blockState)) {
            if (isSolid(abp, level, aboveBlockState)) {
                return true;
            }
        }

        if (IsDoor(dp)) {
            return true;
        }

        // TODO: Windows

        return false;
    }

    private static boolean isSolid(
            BlockPos bp,
            Level level,
            BlockState blockState
    ) {
        return blockState.getShape(level, bp).bounds().getSize() >= 1 && !blockState.propagatesSkylightDown(
                level,
                bp
        ) && !blockState.getCollisionShape(level, bp).isEmpty();
    }

    @Override
    public boolean IsDoor(Position dp) {
        return entity.getLevel().getBlockState(Positions.ToBlock(dp, getY())).getBlock() instanceof DoorBlock;
    }

    public void update(ImmutableMap<Position, Optional<Room>> rooms) {
        this.rooms.update(rooms);
    }

    public Collection<Room> getAll() {
        return this.rooms.getAll();
    }

    @Override
    public void roomAdded(
            Position doorPos,
            Room room
    ) {
        handleRoomChange(room, ParticleTypes.HAPPY_VILLAGER);
        Optional<RoomRecipe> recipe = RecipeDetection.getActiveRecipe(entity.getLevel(), room, this, getY());
        entity.updateActiveRecipe(scanLevel, room, recipe.map(RoomRecipe::getId));
        entity.broadcastMessage(new TranslatableComponent(
                "messages.building.room_created",
                RoomRecipes.getName(recipe),
                doorPos.getUIString()
        ));
    }

    @Override
    public void roomResized(
            Position doorPos,
            Room oldRoom,
            Room newRoom
    ) {

        handleRoomChange(newRoom, ParticleTypes.HAPPY_VILLAGER);
        entity.updateActiveRecipe(scanLevel, oldRoom, Optional.empty());
        Optional<RoomRecipe> recipe = RecipeDetection.getActiveRecipe(entity.getLevel(), newRoom, this, getY());
        entity.updateActiveRecipe(scanLevel, newRoom, recipe.map(RoomRecipe::getId));
        entity.broadcastMessage(new TranslatableComponent(
                "messages.building.room_size_changed",
                RoomRecipes.getName(recipe),
                doorPos.getUIString()
        ));
    }

    @Override
    public void roomDestroyed(
            Position doorPos,
            Room room
    ) {
        Optional<RoomRecipe> recipe = RecipeDetection.getActiveRecipe(entity.getLevel(), room, this, getY());
        entity.broadcastMessage(new TranslatableComponent(
                "messages.building.room_destroyed",
                RoomRecipes.getName(recipe),
                doorPos.getUIString()
        ));
        handleRoomChange(room, ParticleTypes.SMOKE);
        entity.updateActiveRecipe(scanLevel, room, Optional.empty());
    }

    private void handleRoomChange(
            Room room,
            ParticleOptions pType
    ) {
        for (InclusiveSpace space : room.getSpaces()) {
            RoomEffects.renderParticlesBetween(space, (x, z) -> {
                BlockPos bp = new BlockPos(x, getY(), z);
                if (!(entity.getLevel() instanceof ServerLevel sl)) {
                    return;
                }
                if (!entity.getLevel().isEmptyBlock(bp)) {
                    return;
                }
                sl.sendParticles(pType, x, getY(), z, 2, 0, 1, 0, 1);
                sl.sendParticles(pType, x, getY() + 1, z, 2, 0, 1, 0, 1);
            });
        }
    }
}
