package ca.bradj.questown.town;

import ca.bradj.questown.core.advancements.RoomTrigger;
import ca.bradj.questown.core.init.AdvancementsInit;
import ca.bradj.questown.logic.RoomRecipes;
import ca.bradj.questown.logic.TownCycle;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.roomrecipes.core.Room;
import ca.bradj.roomrecipes.core.space.InclusiveSpace;
import ca.bradj.roomrecipes.core.space.Position;
import ca.bradj.roomrecipes.logic.DoorDetection;
import ca.bradj.roomrecipes.recipes.RecipeDetection;
import ca.bradj.roomrecipes.render.RoomEffects;
import ca.bradj.roomrecipes.rooms.ActiveRooms;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Optional;

public class TownRooms implements TownCycle.BlockChecker, DoorDetection.DoorChecker, ActiveRooms.ChangeListener<MCRoom> {

    private RecipeRoomChangeListener changeListener;

    public void setRecipeRoomChangeListener(RecipeRoomChangeListener townRoomsMap) {
        this.changeListener = townRoomsMap;
    }

    public interface RecipeRoomChangeListener {
        void updateRecipeForRoom(
                int scanLevel,
                @Nullable MCRoom oldRoom,
                @Nullable MCRoom newRoom,
                @Nullable RoomRecipeMatch resourceLocation
        );
    }

    private final ActiveRooms<MCRoom> rooms = new ActiveRooms<>();
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
        return WallDetection.IsEmpty(entity.getServerLevel(), dp, getY());
    }

    @Override
    public boolean IsWall(Position dp) {
        return WallDetection.IsWall(entity.getServerLevel(), dp, getY());
    }

    @Override
    public boolean IsDoor(Position dp) {
        return WallDetection.IsDoor(entity.getServerLevel(), dp, getY());
    }

    public void update(ImmutableMap<Position, Optional<MCRoom>> rooms) {
        this.rooms.update(rooms);
    }

    public Collection<MCRoom> getAll() {
        return this.rooms.getAll();
    }

    public Collection<RoomRecipeMatch> getMatches() {
        return this.entity.getMatches();
    }

    @Override
    public void roomAdded(
            Position doorPos,
            MCRoom room
    ) {
        grantAdvancement(doorPos);
        addParticles(entity.getServerLevel(), room, ParticleTypes.HAPPY_VILLAGER);
        Optional<RoomRecipeMatch> recipe = RecipeDetection.getActiveRecipe(entity.getServerLevel(), room, this, getY());
        changeListener.updateRecipeForRoom(scanLevel, room, room, recipe.orElse(null));
        entity.broadcastMessage(new TranslatableComponent(
                "messages.building.room_created",
                RoomRecipes.getName(recipe.map(RoomRecipeMatch::getRecipeID)),
                doorPos.getUIString()
        ));
    }

    private void grantAdvancement(
            Position doorPos
    ) {
        // TODO: Do we need to be smarter than this?
        //  Is it possible we will grant the advancement to the wrong player?
        ServerLevel level = entity.getServerLevel();
        if (level == null) {
            return;
        }
        int y = entity.getBlockPos().getY();
        Player np = level.getNearestPlayer(
                doorPos.x, y, doorPos.z, 8.0D, false
        );
        if (!(np instanceof ServerPlayer sp)) {
            return;
        }
        AdvancementsInit.ROOM_TRIGGER.trigger(
                sp, RoomTrigger.Triggers.FirstRoom
        );
    }

    @Override
    public void roomResized(
            Position doorPos,
            MCRoom oldRoom,
            MCRoom newRoom
    ) {

        addParticles(entity.getServerLevel(), newRoom, ParticleTypes.HAPPY_VILLAGER);
        ServerLevel serverLevel = entity.getServerLevel();
        Optional<RoomRecipeMatch> recipe = RecipeDetection.getActiveRecipe(serverLevel, newRoom, this, getY());
        this.changeListener.updateRecipeForRoom(scanLevel, oldRoom, newRoom, recipe.orElse(null));
        entity.broadcastMessage(new TranslatableComponent(
                "messages.building.room_size_changed",
                RoomRecipes.getName(recipe.map(RoomRecipeMatch::getRecipeID)),
                doorPos.getUIString()
        ));
    }

    @Override
    public void roomDestroyed(
            Position doorPos,
            MCRoom room
    ) {
        Optional<RoomRecipeMatch> recipe = RecipeDetection.getActiveRecipe(entity.getServerLevel(), room, this, getY());
        entity.broadcastMessage(new TranslatableComponent(
                "messages.building.room_destroyed",
                RoomRecipes.getName(recipe.map(RoomRecipeMatch::getRecipeID)),
                doorPos.getUIString()
        ));
        addParticles(entity.getServerLevel(), room, ParticleTypes.SMOKE);
        changeListener.updateRecipeForRoom(scanLevel, room, null, null);
    }


    public static void addParticles(
            ServerLevel sl,
            MCRoom room,
            ParticleOptions pType
    ) {
        for (InclusiveSpace space : room.getSpaces()) {
            RoomEffects.renderParticlesBetween(space, (x, z) -> {
                BlockPos bp = new BlockPos(x, room.yCoord, z);
                if (!sl.isEmptyBlock(bp)) {
                    return;
                }
                sl.sendParticles(pType, x, room.yCoord, z, 2, 0, 1, 0, 1);
                sl.sendParticles(pType, x, room.yCoord + 1, z, 2, 0, 1, 0, 1);
            });
        }
    }
}
