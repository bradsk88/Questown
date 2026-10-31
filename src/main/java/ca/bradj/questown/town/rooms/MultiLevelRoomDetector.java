package ca.bradj.questown.town.rooms;

import ca.bradj.questown.core.Config;
import ca.bradj.questown.town.WallDetection;
import ca.bradj.questown.town.entity.TownRooms;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatches;
import ca.bradj.roomrecipes.core.Room;
import ca.bradj.roomrecipes.core.space.InclusiveSpace;
import ca.bradj.roomrecipes.core.space.Position;
import ca.bradj.roomrecipes.logic.InclusiveSpaces;
import ca.bradj.roomrecipes.logic.LevelRoomDetector;
import ca.bradj.roomrecipes.recipes.ActiveRecipes;
import ca.bradj.roomrecipes.recipes.RecipeDetection;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class MultiLevelRoomDetector {
    private final ServerLevel level;
    private final Map<Position, Optional<MCRoom>> foundRooms = new HashMap<>();
    private final LinkedBlockingQueue<RoomWithlevel> roomsToScan = new LinkedBlockingQueue<>();
    private final LinkedBlockingQueue<DetectorWithLevel> roomDetectors = new LinkedBlockingQueue<>();
    private final Function<Integer, ActiveRecipes<MCRoom, RoomRecipeMatch<MCRoom>>> recipes;
    private final int flagY;
    private final Predicate<TownPosition> isDoor;
    private final BiConsumer<Integer, ImmutableMap<Position, Optional<MCRoom>>> newRoomsHandler;
    private @Nullable Long trueStart;
    private BiConsumer<Integer, ImmutableMap<Position, String>> artSink;

    public void setArtSink(BiConsumer<Integer, ImmutableMap<Position, String>> artSink) {
        this.artSink = artSink;
    }

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

    public MultiLevelRoomDetector(
            ServerLevel level,
            int flagY,
            Predicate<TownPosition> isWall,
            Predicate<TownPosition> isDoor,
            BiConsumer<Integer, ImmutableMap<Position, Optional<MCRoom>>> newRoomsHandler,
            Function<Integer, ActiveRecipes<MCRoom, RoomRecipeMatch<MCRoom>>> getRecipes,
            Map<Integer, Collection<Position>> doorsAtLevel,
            boolean enableDebugArt
    ) {
        this.isDoor = isDoor;
        this.newRoomsHandler = newRoomsHandler;
        this.level = level;
        this.flagY = flagY;
        this.recipes = getRecipes;
        doorsAtLevel.forEach((yOffset, doors) -> this.roomDetectors.add(new DetectorWithLevel(
                new LevelRoomDetector(
                        doors,
                        Config.MAX_ROOM_DIMENSION.get(),
                        Config.MAX_ROOM_SCAN_ITERATIONS.get(),
                        p -> isWall.test(new TownPosition(p.x, p.z, yOffset)),
                        enableDebugArt,
                        null
                ), yOffset)));
    }

    public boolean proceed(Function<Integer, @Nullable TownRooms> currentRoomsAtYOffset) {
        if (this.trueStart == null) {
            this.trueStart = System.currentTimeMillis();
        }
        if (roomDetectors.isEmpty() && roomsToScan.isEmpty() && foundRooms.isEmpty()) {
            return true;
        }

        if (!roomDetectors.isEmpty()) {
            DetectorWithLevel nextDetector = roomDetectors.remove();
            TownRooms cur = currentRoomsAtYOffset.apply(nextDetector.scanLevel);
            @Nullable ImmutableMap<Position, Optional<Room>> result;
            if (cur != null) {
                result = nextDetector.detector.proceed(p -> cur.get(p).map(v -> v));
            } else {
                result = nextDetector.detector.proceed();
            }
            if (result != null) {
                ImmutableMap<Position, Optional<MCRoom>> build = handleNewRooms(result, nextDetector.scanLevel);
                this.newRoomsHandler.accept(nextDetector.scanLevel, build);
                if (this.artSink != null) {
                    this.artSink.accept(nextDetector.scanLevel, nextDetector.detector.getDebugArt(true));
                }
            } else {
                roomDetectors.add(nextDetector);
            }
        }

        if (!roomsToScan.isEmpty()) {
            RoomWithlevel room = roomsToScan.remove();
            Optional<RoomRecipeMatches<MCRoom>> recipe = RecipeDetection.getActiveRecipes(
                    level,
                    room.room,
                    false
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
            @NotNull ImmutableMap<Position, Optional<Room>> result,
            int scanLevel
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
            }).filter(r -> hasWalkableInterior(r, scanLevel));
            b.put(k, value);
            value.ifPresent(r -> roomsToScan.add(new RoomWithlevel(r, scanLevel)));
        });
        ImmutableMap<Position, Optional<MCRoom>> build = b.build();
        return build;
    }

    /**
     * Rejects a detected room whose interior a two-block-tall villager cannot stand in.
     * The 2D wall-walk treats a cell as interior when it is empty at the feet level OR the
     * head level, so a room roofed only one block above its floor still "detects" — but no
     * villager can enter it. This requires at least
     * {@link Config#MIN_ROOM_WALKABLE_FRACTION} of the interior floor cells to be standable
     * (passable at both feet and head), so a room with a low overhang over only part of its
     * floor still counts while a fully low-roofed room is rejected (surfaces as no-room).
     *
     * <p>A cell <em>filled at the feet level</em> by a block (the chest in a 3x3 storeroom's
     * single interior cell) is the room's contents, not a floor, so it is excluded from the
     * denominator rather than counted against it. The low-roof case is air at the feet (so it
     * still counts and still fails); a fully furnished interior has no open floor cells at all
     * and is accepted. (day-1 playtest 2026-10-11: the 3x3 storeroom's chest cell read as
     * unwalkable and the filter destroyed the room the moment it matched.)
     */
    private boolean hasWalkableInterior(
            MCRoom room,
            int scanLevel
    ) {
        double fraction = Config.MIN_ROOM_WALKABLE_FRACTION.get();
        if (fraction <= 0.0) {
            return true;
        }
        int y = flagY + scanLevel;
        int floorCells = 0;
        int walkable = 0;
        for (InclusiveSpace space : room.getSpaces()) {
            for (Position p : InclusiveSpaces.getPositions(
                    space,
                    InclusiveSpaces.PositionType.INTERIOR_ONLY
            )) {
                if (!WallDetection.IsFloorOpen(level, p, y)) {
                    continue;
                }
                floorCells++;
                if (WallDetection.IsWalkable(level, p, y)) {
                    walkable++;
                }
            }
        }
        if (floorCells == 0) {
            return true;
        }
        int required = Math.max(1, (int) Math.ceil(floorCells * fraction));
        return walkable >= required;
    }
}
