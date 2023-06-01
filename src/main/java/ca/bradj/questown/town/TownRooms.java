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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Optional;

public class TownRooms implements TownCycle.BlockChecker, DoorDetection.DoorChecker, ActiveRooms.ChangeListener {

    private RecipeRoomChangeListener changeListener;

    public void setRecipeRoomChangeListener(RecipeRoomChangeListener townRoomsMap) {
        this.changeListener = townRoomsMap;
    }

    public interface RecipeRoomChangeListener {
        void updateRecipeForRoom(int scanLevel, Position doorPos, @Nullable ResourceLocation resourceLocation);
    }

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
        return entity.getTownFlagBasePos().getY() + scanLevel;
    }

    @Override
    public boolean IsEmpty(Position dp) {
        BlockPos bp = Positions.ToBlock(dp, getY());
        BlockPos abp = bp.above();
        boolean empty = entity.getServerLevel().isEmptyBlock(bp);
        boolean emptyAbove = entity.getServerLevel().isEmptyBlock(abp);
        return empty || emptyAbove;
    }

    @Override
    public boolean IsWall(Position dp) {
        BlockPos bp = Positions.ToBlock(dp, getY());
        BlockPos abp = bp.above();
        if (this.IsEmpty(dp)) {
            return false;
        }
        Level level = entity.getServerLevel();
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
        BlockState bs = entity.getServerLevel().getBlockState(Positions.ToBlock(dp, getY()));
        return DoubleBlockHalf.LOWER.equals(bs.getOptionalValue(DoorBlock.HALF).orElse(null));
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
        Optional<RoomRecipe> recipe = RecipeDetection.getActiveRecipe(entity.getServerLevel(), room, this, getY());
        changeListener.updateRecipeForRoom(scanLevel, doorPos, recipe.map(RoomRecipe::getId).orElse(null));
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
        Optional<RoomRecipe> recipe = RecipeDetection.getActiveRecipe(entity.getServerLevel(), newRoom, this, getY());
        this.changeListener.updateRecipeForRoom(scanLevel, doorPos, recipe.map(RoomRecipe::getId).orElse(null));
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
        Optional<RoomRecipe> recipe = RecipeDetection.getActiveRecipe(entity.getServerLevel(), room, this, getY());
        entity.broadcastMessage(new TranslatableComponent(
                "messages.building.room_destroyed",
                RoomRecipes.getName(recipe),
                doorPos.getUIString()
        ));
        handleRoomChange(room, ParticleTypes.SMOKE);
        changeListener.updateRecipeForRoom(scanLevel, doorPos, null);
    }


    private void handleRoomChange(
            Room room,
            ParticleOptions pType
    ) {
        for (InclusiveSpace space : room.getSpaces()) {
            RoomEffects.renderParticlesBetween(space, (x, z) -> {
                BlockPos bp = new BlockPos(x, getY(), z);
                ServerLevel sl = entity.getServerLevel();
                if (!sl.isEmptyBlock(bp)) {
                    return;
                }
                sl.sendParticles(pType, x, getY(), z, 2, 0, 1, 0, 1);
                sl.sendParticles(pType, x, getY() + 1, z, 2, 0, 1, 0, 1);
            });
        }
    }
}
