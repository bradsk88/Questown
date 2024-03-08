package ca.bradj.questown.town.rooms;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.logic.TownCycle;
import ca.bradj.questown.town.TownRooms;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.roomrecipes.core.Room;
import ca.bradj.roomrecipes.core.space.Position;
import ca.bradj.roomrecipes.recipes.ActiveRecipes;
import ca.bradj.roomrecipes.recipes.RecipeDetection;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Supplier;

public class PendingTownRooms {
    private final ServerLevel level;
    private final Position scanAroundPos;
    private final int y;
    private final LinkedBlockingQueue<Position> doorsToScan = new LinkedBlockingQueue<>();
    private final Map<Position, Optional<MCRoom>> foundRooms = new HashMap<>();
    private final LinkedBlockingQueue<MCRoom> roomsToScan = new LinkedBlockingQueue<>();
    private final Supplier<TownRooms> rooms;
    private final Supplier<ActiveRecipes<MCRoom, RoomRecipeMatch<MCRoom>>> recipes;

    public PendingTownRooms(
            ServerLevel level,
            Position scanAroundPos,
            Supplier<TownRooms> getRooms,
            Supplier<ActiveRecipes<MCRoom, RoomRecipeMatch<MCRoom>>> getRecipes,
            int y,
            Set<Position> doorsAtLevel
    ) {
        this.level = level;
        this.scanAroundPos = scanAroundPos;
        this.rooms = getRooms;
        this.recipes = getRecipes;
        this.y = y;
        this.doorsToScan.addAll(doorsAtLevel);
    }

    public boolean proceed() {
        if (roomsToScan.isEmpty() && doorsToScan.isEmpty() && foundRooms.isEmpty()) {
            log("no more rooms or doors");
            return true;
        }

        TownRooms ars = this.rooms.get();

        if (!roomsToScan.isEmpty()) {
            MCRoom room = roomsToScan.remove();
            Optional<RoomRecipeMatch<MCRoom>> recipe = RecipeDetection.getActiveRecipe(
                    level,
                    room,
                    ars
            );
            ActiveRecipes<MCRoom, RoomRecipeMatch<MCRoom>> rs = recipes.get();
            rs.update(room, room, recipe.orElse(null));
            log("Updated active recipe for {}", room);
            return false;
        }

        if (doorsToScan.isEmpty()) {
            ars.update(ImmutableMap.copyOf(foundRooms));
            foundRooms.forEach((k, v) -> v.ifPresent(roomsToScan::add));
            foundRooms.clear();
            log("Finished scanning doors. {} rooms to scan.", roomsToScan.size());
            return false;
        }

        Position door = doorsToScan.remove();

        ImmutableMap<Position, Optional<Room>> rooms = TownCycle.findRooms(
                scanAroundPos, ars, ImmutableList.of(door)
        );

        if (rooms.isEmpty()) {
            log("No rooms found for {}", door);
            return false;
        }

        Optional<Room> foundRoom = rooms.get(door);
        if (foundRoom == null || foundRoom.isEmpty()) {
            log("No rooms found for {}", door);
            return false;
        }
        foundRooms.put(door, foundRoom.map(v -> new MCRoom(v.getDoorPos(), v.getSpaces(), this.y)));
        return false;
    }

    private void log(String msg, Object... args) {
        if (!Config.LOG_ROOM_SCANNING.get()) {
            return;
        }
        QT.PROFILE_LOGGER.debug(msg, args);
    }
}
