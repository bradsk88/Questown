package ca.bradj.questown.town.rooms;

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
import java.util.function.Function;
import java.util.function.Supplier;

public class PendingTownRooms {
    private final ServerLevel level;
    private final Map<Position, Optional<MCRoom>> foundRooms = new HashMap<>();
    private final LinkedBlockingQueue<RoomWithlevel> roomsToScan = new LinkedBlockingQueue<>();
    private final LinkedBlockingQueue<DetectorWithLevel> roomDetectors = new LinkedBlockingQueue<>();
    private final Function<Integer, ActiveRecipes<MCRoom, RoomRecipeMatch<MCRoom>>> recipes;
    private final Function<Integer, TownRooms> rooms;
    private final int flagY;
    private @Nullable Long trueStart;

    private record DetectorWithLevel(
            LevelRoomDetector detector,
            Integer scanLevel
    ) {
    }

    private record RoomWithlevel(
            MCRoom room,
            Integer scanLevel
    ) {
    }

    public PendingTownRooms(
            ServerLevel level, int flagY,
            Function<Integer, TownRooms> rooms,
            Collection<Integer> scanLevels,
            Function<Integer, ActiveRecipes<MCRoom, RoomRecipeMatch<MCRoom>>> getRecipes,
            Map<Integer, Collection<Position>> doorsAtLevel
    ) {
        this.level = level;
        this.flagY = flagY;
        this.recipes = getRecipes;
        this.rooms = rooms;
        scanLevels.forEach(yOffset -> this.roomDetectors.add(new DetectorWithLevel(
                new LevelRoomDetector(
                        doorsAtLevel.get(yOffset),
                        Config.MAX_ROOM_DIMENSION.get(),
                        Config.MAX_ROOM_SCAN_ITERATIONS.get(),
                        p -> rooms.apply(yOffset)
                                  .IsWall(p),
                        false,
                        null
                ), yOffset)));
    }

    public boolean proceed() {
        if (this.trueStart == null) {
            this.trueStart = System.currentTimeMillis();
        }
        if (roomDetectors.isEmpty() && roomsToScan.isEmpty() && foundRooms.isEmpty()) {
            return true;
        }

        if (!roomDetectors.isEmpty()) {
            DetectorWithLevel nextDetector = roomDetectors.remove();
            @Nullable ImmutableMap<Position, Optional<Room>> result = nextDetector.detector.proceed();
            if (result != null) {
                ImmutableMap<Position, Optional<MCRoom>> build = handleNewRooms(result, nextDetector.scanLevel);
                rooms.apply(nextDetector.scanLevel)
                     .update(build);
            } else {
                roomDetectors.add(nextDetector);
            }
        }

        if (!roomsToScan.isEmpty()) {
            RoomWithlevel room = roomsToScan.remove();
            Optional<RoomRecipeMatch<MCRoom>> recipe = RecipeDetection.getActiveRecipe(
                    level,
                    room.room,
                    rooms.apply(room.scanLevel)
            );
            ActiveRecipes<MCRoom, RoomRecipeMatch<MCRoom>> rs = recipes.apply(room.scanLevel);
            rs.update(
                    room.room,
                    room.room,
                    recipe.orElse(null)
            );
            return false;
        }

        return false;
    }

    @NotNull
    private ImmutableMap<Position, Optional<MCRoom>> handleNewRooms(
            @NotNull ImmutableMap<Position, Optional<Room>> result, int scanLevel
    ) {
        ImmutableMap.Builder<Position, Optional<MCRoom>> b = ImmutableMap.builder();
        result.forEach((k, v) -> {
            Optional<MCRoom> value = v.map(z -> {
                MCRoom mcRoom = new MCRoom(
                        z.getDoorPos(),
                        z.getSpaces(),
                        flagY + scanLevel
                );
                return mcRoom;
            });
            b.put(
                    k,
                    value
            );
            value.ifPresent(r -> roomsToScan.add(new RoomWithlevel(r, scanLevel)));
        });
        ImmutableMap<Position, Optional<MCRoom>> build = b.build();
        return build;
    }
}
