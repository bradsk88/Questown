package ca.bradj.questown.town.rooms;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.town.TownRooms;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.roomrecipes.core.Room;
import ca.bradj.roomrecipes.core.space.Position;
import ca.bradj.roomrecipes.logic.LevelRoomDetector;
import ca.bradj.roomrecipes.recipes.ActiveRecipes;
import ca.bradj.roomrecipes.recipes.RecipeDetection;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Supplier;

public class PendingTownRooms {
    private final ServerLevel level;
    private final Position scanAroundPos;
    private final int y;
    private final Map<Position, Optional<MCRoom>> foundRooms = new HashMap<>();
    private final LinkedBlockingQueue<MCRoom> roomsToScan = new LinkedBlockingQueue<>();
    private final LevelRoomDetector roomDetector;
    private final Supplier<ActiveRecipes<MCRoom, RoomRecipeMatch<MCRoom>>> recipes;
    private final TownRooms rooms;
    private @Nullable Long trueStart;

    public PendingTownRooms(
            ServerLevel level,
            Position scanAroundPos,
            TownRooms rooms,
            Supplier<ActiveRecipes<MCRoom, RoomRecipeMatch<MCRoom>>> getRecipes,
            int y,
            Set<Position> doorsAtLevel
    ) {
        this.level = level;
        this.scanAroundPos = scanAroundPos;
        this.recipes = getRecipes;
        this.y = y;
        this.rooms = rooms;
        this.roomDetector = new LevelRoomDetector(
                doorsAtLevel,
                Config.MAX_ROOM_DIMENSION.get(),
                Config.MAX_ROOM_SCAN_ITERATIONS.get(),
                rooms::IsWall,
                Config.ENABLE_DEBUG_ART.get()
        );
    }

    public boolean proceed() {
        if (this.trueStart == null) {
            this.trueStart = System.currentTimeMillis();
        }
        if (roomDetector.isDone() && roomsToScan.isEmpty() && foundRooms.isEmpty()) {
            log(
                    trueStart,
                    "No more rooms or doors. Town scan is complete after {} iterations",
                    roomDetector.iterationsUsed()
            );
            if (Config.ENABLE_DEBUG_ART.get()) {
                roomDetector.getDebugArt(true).forEach(
                        (k, v) -> QT.FLAG_LOGGER.debug("Art for {}\n{}", k.getUIString(), v)
                );
            }
            return true;
        }

        if (!roomsToScan.isEmpty()) {
            long start = System.currentTimeMillis();
            MCRoom room = roomsToScan.remove();
            Optional<RoomRecipeMatch<MCRoom>> recipe = RecipeDetection.getActiveRecipe(
                    level,
                    room,
                    rooms
            );
            ActiveRecipes<MCRoom, RoomRecipeMatch<MCRoom>> rs = recipes.get();
            rs.update(
                    room,
                    room,
                    recipe.orElse(null)
            );
            log(
                    start,
                    "Updated active recipe for {} to {}",
                    room,
                    recipe.map(RoomRecipeMatch::getRecipeID)
            );
            return false;
        }

        long start = System.currentTimeMillis();
        @Nullable ImmutableMap<Position, Optional<Room>> result = roomDetector.proceed();
        if (result != null) {
            log(
                    start,
                    "Room detector finished last iteration on level {}. Result: {}",
                    y,
                    result.values()
            );
            start = System.currentTimeMillis();
            ImmutableMap<Position, Optional<MCRoom>> build = handleNewRooms(result);
            rooms.update(build);
            log(
                    start,
                    "Queued {} rooms to scan for active recipes",
                    roomsToScan.size()
            );
        } else {
            log(
                    start,
                    "Room scanner iteration"
            );
        }
        return false;
    }

    @NotNull
    private ImmutableMap<Position, Optional<MCRoom>> handleNewRooms(
            @NotNull ImmutableMap<Position, Optional<Room>> result
    ) {
        ImmutableMap.Builder<Position, Optional<MCRoom>> b = ImmutableMap.builder();
        result.forEach((k, v) -> {
            Optional<MCRoom> value = v.map(z -> {
                MCRoom mcRoom = new MCRoom(
                        z.getDoorPos(),
                        z.getSpaces(),
                        y
                );
                return mcRoom;
            });
            b.put(
                    k,
                    value
            );
            value.ifPresent(roomsToScan::add);
        });
        ImmutableMap<Position, Optional<MCRoom>> build = b.build();
        return build;
    }

    private void log(
            long start,
            String msg,
            Object... args
    ) {
        long took = System.currentTimeMillis() - start;
        if (!Config.LOG_ROOM_SCANNING.get()) {
            return;
        }

        List<Object> l = new ArrayList<>(Arrays.asList(args));
        l.add(took);

        QT.PROFILE_LOGGER.debug(
                String.format(
                        "%s: [Took: {}]",
                        msg
                ),
                l.toArray()
        );
    }
}
