package ca.bradj.questown.town;

import ca.bradj.questown.Questown;
import ca.bradj.questown.logic.TownCycle;
import ca.bradj.roomrecipes.adapter.Positions;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.roomrecipes.core.Room;
import ca.bradj.roomrecipes.core.space.Position;
import ca.bradj.roomrecipes.recipes.ActiveRecipes;
import ca.bradj.roomrecipes.recipes.RecipeDetection;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TownRoomsMap implements TownRooms.RecipeRoomChangeListener {
    private final Map<Integer, TownRooms> activeRooms = new HashMap<>();
    private final Map<Integer, ActiveRecipes<RoomRecipeMatch>> activeRecipes = new HashMap<>();
    private int scanLevel = 0;
    private int scanBuffer = 0;
    private TownFlagBlockEntity changeListener;
    // FIXME: Store on NBT
    private Set<BlockPos> registeredDoors = new HashSet<>();

    TownRoomsMap(TownFlagBlockEntity entity) {
        changeListener = entity;
//        getOrCreateRooms(0);
    }

    private void updateActiveRooms(
            ServerLevel level,
            @Nullable Position scanAroundPos,
            int scanLevel,
            int scanY,
            Set<Position> registeredDoors
    ) {
        TownRooms ars = getOrCreateRooms(scanLevel);

        ImmutableMap<Position, Optional<Room>> rooms = TownCycle.findRooms(
                scanAroundPos, ars, registeredDoors
        );
        ars.update(rooms);

        ars.getAll().forEach(room -> {
            Optional<RoomRecipeMatch> recipe = RecipeDetection.getActiveRecipe(
                    level,
                    room,
                    ars,
                    scanY
            );
            activeRecipes.get(scanLevel).update(room.getDoorPos(), recipe.orElse(null));
        });
    }

    private TownRooms getOrCreateRooms(int scanLevel) {
        if (!activeRecipes.containsKey(scanLevel)) {
            ActiveRecipes<RoomRecipeMatch> v = new ActiveRecipes<>();
            activeRecipes.put(scanLevel, v);
            v.addChangeListener(changeListener);
        }

        if (!activeRooms.containsKey(scanLevel)) {
            TownRooms v = new TownRooms(
                    scanLevel,
                    changeListener
            ); // TODO: Consider using listener instead of passing entity
            activeRooms.put(scanLevel, v);
            v.setRecipeRoomChangeListener(this);
        }

        return activeRooms.get(scanLevel);
    }

    public void tick(
            ServerLevel level,
            BlockPos blockPos
    ) {

//        scanBuffer = (scanBuffer + 1) % 2;
//        if (scanBuffer == 0) {
        // TODO: Use a FIFO queue and only run one iteration (y level) per tick
        scanLevel = (scanLevel + 1) % 5;
//        }
        Position scanAroundPos = Positions.FromBlockPos(blockPos);
        Set<Position> doorsAtZero = registeredDoors.stream()
                .filter(v -> v.getY() == 0)
                .map(Positions::FromBlockPos)
                .collect(Collectors.toSet());
        updateActiveRooms(level, scanAroundPos, 0, blockPos.getY(), doorsAtZero);

        if (scanLevel != 0) {
            Set<Position> doorsAtLevel = registeredDoors.stream()
                    .filter(v -> v.getY() == scanLevel)
                    .map(Positions::FromBlockPos)
                    .collect(Collectors.toSet());
            int y = blockPos.offset(0, scanLevel, 0).getY();
            updateActiveRooms(level, scanAroundPos, scanLevel, y, doorsAtLevel);
        }

        for (int y : registeredDoors.stream().map(Vec3i::getY).distinct().toList()) {
            if (y == scanLevel || y == 0) {
                continue;
            }
            Set<Position> doorsAtLevel = registeredDoors.stream()
                    .filter(v -> v.getY() == y)
                    .map(Positions::FromBlockPos)
                    .collect(Collectors.toSet());
            int y1 = blockPos.offset(0, y, 0).getY();
            updateActiveRooms(level, null, y, y1, doorsAtLevel);
        }
    }

    public void initialize(
            TownFlagBlockEntity owner,
            Map<Integer, ActiveRecipes<RoomRecipeMatch>> ars
    ) {
        if (this.activeRecipes.size() > 0) {
            throw new IllegalStateException("Double initialization");
        }
        this.activeRecipes.putAll(ars);
        for (ActiveRecipes<RoomRecipeMatch> r : ars.values()) {
            r.addChangeListener(owner);
        }
    }

    /**
     * @deprecated Used for a migration only.
     */
    public ActiveRecipes<RoomRecipeMatch> getRecipes(int i) {
        return activeRecipes.get(i);
    }

    public void addChangeListener(TownFlagBlockEntity townFlagBlockEntity) {
        this.changeListener = townFlagBlockEntity; // TODO: Interface
    }

    public Collection<Room> getAllRooms() {
        return this.activeRooms.values().stream().map(TownRooms::getAll).flatMap(Collection::stream).toList();
    }

    @Override
    public void updateRecipeForRoom(
            int scanLevel,
            Position doorPos,
            @Nullable RoomRecipeMatch resourceLocation
    ) {
        this.activeRecipes.get(scanLevel).update(doorPos, resourceLocation);
    }

    public int numRecipes() {
        return this.activeRecipes.size();
    }

    public Collection<RoomRecipeMatch> getAllMatches() {
        Stream<RoomRecipeMatch> objectStream = this.activeRecipes.values()
                .stream()
                .map(ActiveRecipes::entrySet)
                .flatMap(v -> v.stream().map(Map.Entry::getValue));
        return objectStream.collect(Collectors.toSet());
    }

    public void registerDoor(Position p, int scanLevel) {
        registeredDoors.add(new BlockPos(p.x, scanLevel, p.z)); // FIXME: Define our own type to avoid confusion
        Questown.LOGGER.debug("Door was registered at x={}, z={}, scanLevel={}", p.x, p.z, scanLevel);
    }
}
