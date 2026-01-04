package ca.bradj.questown.town.entity;

import ca.bradj.questown.core.advancements.RoomTrigger;
import ca.bradj.questown.core.init.AdvancementsInit;
import ca.bradj.questown.logic.RoomRecipes;
import ca.bradj.questown.logic.TownCycle;
import ca.bradj.questown.roomrecipes.Matches;
import ca.bradj.questown.town.WallDetection;
import ca.bradj.roomrecipes.adapter.Positions;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatches;
import ca.bradj.roomrecipes.core.space.InclusiveSpace;
import ca.bradj.roomrecipes.core.space.Position;
import ca.bradj.roomrecipes.logic.DoorDetection;
import ca.bradj.roomrecipes.recipes.RecipeDetection;
import ca.bradj.roomrecipes.recipes.RoomAnnouncing;
import ca.bradj.roomrecipes.recipes.RoomRecipe;
import ca.bradj.roomrecipes.render.RoomEffects;
import ca.bradj.roomrecipes.rooms.ActiveRooms;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Supplier;

public class TownRooms implements
        TownCycle.BlockChecker,
        DoorDetection.DoorChecker,
        ActiveRooms.ChangeListener<MCRoom>,
        RoomAnnouncing {

    private final List<RecipeRoomChangeListener> changeListeners = new ArrayList<>();

    public void addRecipeRoomChangeListener(RecipeRoomChangeListener cl) {
        this.changeListeners.add(cl);
    }

    public Optional<MCRoom> get(Position position) {
        return Optional.ofNullable(rooms.get(position));
    }

    private final Stack<Position> roomsToSkipInitialAnnounce = new Stack<>();

    @Override
    public void skipAnnounceOnFirstDetect(Collection<Position> collection) {
        roomsToSkipInitialAnnounce.addAll(collection);
    }

    public interface RecipeRoomChangeListener {
        void updateRecipeForRoom(
                int scanLevel,
                @Nullable MCRoom oldRoom,
                @Nullable MCRoom newRoom,
                @Nullable RoomRecipeMatch<MCRoom> resourceLocation
        );
    }

    private final ActiveRooms<MCRoom> rooms = new ActiveRooms<>();
    private final int scanLevel;
    private final Supplier<TownFlagBlockEntity> entitySupplier;

    public TownRooms(
            int scanLevel,
            Supplier<TownFlagBlockEntity> entitySupplier
    ) {
        this.entitySupplier = entitySupplier;
        this.scanLevel = scanLevel;
        rooms.addChangeListener(this);
    }

    int getY() {
        return entitySupplier.get().getTownFlagBasePos().getY() + scanLevel;
    }

    @Override
    public boolean IsEmpty(Position dp) {
        return WallDetection.IsEmpty(entitySupplier.get().getServerLevel(), dp, getY());
    }

    @Override
    public boolean IsWall(Position dp) {
        return WallDetection.IsWall(entitySupplier.get().getServerLevel(), dp, getY());
    }

    @Override
    public boolean IsDoor(Position dp) {
        return WallDetection.IsDoor(entitySupplier.get().getServerLevel(), dp, getY());
    }

    public void update(ImmutableMap<Position, Optional<MCRoom>> rooms) {
        this.rooms.update(rooms);
    }

    public Collection<MCRoom> getAll() {
        return this.rooms.getAll();
    }

    @Override
    public void roomAdded(
            Position doorPos,
            MCRoom room
    ) {
        TownFlagBlockEntity entity = entitySupplier.get();
        grantAdvancement(doorPos);
        ServerLevel l = entity.getServerLevel();
        addParticles(l, room, ParticleTypes.HAPPY_VILLAGER);
        Optional<RoomRecipeMatches<MCRoom>> recipe = getActiveRecipes(l, room);
        if (roomsToSkipInitialAnnounce.contains(room.doorPos)) {
            roomsToSkipInitialAnnounce.remove(room.doorPos);
        } else {
            changeListeners.forEach(
                    cl -> cl.updateRecipeForRoom(scanLevel, room, room, recipe.orElse(null))
            );
            Optional<ResourceLocation> name = Optional.empty();
            if (recipe.isPresent()) {
                name = Matches.getTopMatch(this::recipesFromLevel, recipe.get());
            }
            entity.messages.roomCreated(name.orElse(null), doorPos);
        }
    }


    private Map<ResourceLocation, RoomRecipe> recipesFromLevel() {
        return RoomRecipes.hydrate(entitySupplier.get().getServerLevel().getRecipeManager(), true);
    }

    protected Optional<RoomRecipeMatches<MCRoom>> getActiveRecipes(
            ServerLevel level,
            MCRoom room
    ) {
        return RecipeDetection.getActiveRecipes(level, room, false);
    }

    private void grantAdvancement(
            Position doorPos
    ) {
        TownFlagBlockEntity entity = entitySupplier.get();
        // TODO: Do we need to be smarter than this?
        //  Is it possible we will grant the advancement to the wrong player?
        ServerLevel level = entity.getServerLevel();
        if (level == null) {
            return;
        }
        AdvancementsInit.ROOM_TRIGGER.triggerForNearestPlayer(
                level, RoomTrigger.Triggers.FirstRoom,
                Positions.ToBlock(doorPos, entity.getY())
        );
    }

    @Override
        public void roomResized(
            Position doorPos,
            MCRoom oldRoom,
            MCRoom newRoom
    ) {
        TownFlagBlockEntity entity = entitySupplier.get();
        addParticles(entity.getServerLevel(), newRoom, ParticleTypes.HAPPY_VILLAGER);
        ServerLevel l = entity.getServerLevel();
        Optional<RoomRecipeMatches<MCRoom>> recipe = getActiveRecipes(l, newRoom);
        this.changeListeners.forEach(
                changeListener -> changeListener.updateRecipeForRoom(
                        scanLevel, oldRoom, newRoom, recipe.orElse(null)
                )
        );
        Optional<ResourceLocation> roomName = Optional.empty();
        if (recipe.isPresent()) {
            roomName = Matches.getTopMatch(this::recipesFromLevel, recipe.get());
        }
        entity.messages.roomSizeChanged(roomName.orElse(null), doorPos);
    }

    public void recheckRecipes(Supplier<ServerLevel> level) {
        for (MCRoom newRoom : rooms.getAll()) {
            Optional<RoomRecipeMatches<MCRoom>> recipe = getActiveRecipes(level.get(), newRoom);
            this.changeListeners.forEach(
                    changeListener -> changeListener.updateRecipeForRoom(
                            scanLevel, newRoom, newRoom, recipe.orElse(null)
                    )
            );
        }
    }

    @Override
    public void roomDestroyed(
            Position doorPos,
            MCRoom room
    ) {
        TownFlagBlockEntity entity = entitySupplier.get();
        ServerLevel l = entity.getServerLevel();
        Optional<RoomRecipeMatches<MCRoom>> recipe = getActiveRecipes(l, room);
        Optional<ResourceLocation> roomName = Optional.empty();
        if (recipe.isPresent()) {
            roomName = Matches.getTopMatch(this::recipesFromLevel, recipe.get());
        }

        entity.messages.roomDestroyed(roomName.orElse(null), doorPos);
        addParticles(l, room, ParticleTypes.SMOKE);
        changeListeners.forEach(
                cl -> cl.updateRecipeForRoom(scanLevel, room, null, null)
        );
    }


    public static void addParticles(
            ServerLevel sl,
            MCRoom room,
            ParticleOptions pType
    ) {
        for (InclusiveSpace space : room.getSpaces()) {
            RoomEffects.renderParticlesBetween(
                    space, (x, z) -> {
                        BlockPos bp = new BlockPos(x, room.yCoord, z);
                        if (!sl.isEmptyBlock(bp)) {
                            return;
                        }
                        sl.sendParticles(pType, x, room.yCoord, z, 2, 0, 1, 0, 1);
                        sl.sendParticles(pType, x, room.yCoord + 1, z, 2, 0, 1, 0, 1);
                    }
            );
        }
    }
}
