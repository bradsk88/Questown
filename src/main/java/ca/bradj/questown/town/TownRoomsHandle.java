package ca.bradj.questown.town;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.roomrecipes.Spaces;
import ca.bradj.questown.town.interfaces.RoomsHolder;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.rooms.MultiLevelRoomDetector;
import ca.bradj.questown.town.rooms.TownPosition;
import ca.bradj.questown.town.rooms.TownRoomsMap;
import ca.bradj.questown.town.special.SpecialQuests;
import ca.bradj.roomrecipes.adapter.Positions;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.roomrecipes.core.Room;
import ca.bradj.roomrecipes.core.space.Position;
import ca.bradj.roomrecipes.logic.LevelRoomDetector;
import ca.bradj.roomrecipes.recipes.ActiveRecipes;
import ca.bradj.roomrecipes.recipes.RecipeDetection;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Function;
import java.util.function.Supplier;

public class TownRoomsHandle implements RoomsHolder, ActiveRecipes.ChangeListener<MCRoom, RoomRecipeMatch<MCRoom>>,
        Supplier<TownFlagBlockEntity> {

    private final TownRoomsMap roomsMap = new TownRoomsMap();
    @Nullable
    private TownFlagBlockEntity town;
    @Nullable
    private MCRoom flagMetaRoom;

    public void initializeNew(TownFlagBlockEntity t) {
        this.town = t;
        this.flagMetaRoom = Spaces.metaRoomAround(t.getBlockPos(), 2);
        roomsMap.initializeNew(t);
        roomsMap.addRecipeListener(this);
    }

    /**
     * Only safe to call after initialize
     */
    private @NotNull TownFlagBlockEntity unsafeGetTown() {
        if (town == null) {
            throw new IllegalStateException("Town has not been initialized on quest handle yet");
        }
        return town;
    }

    @Override
    public Collection<RoomRecipeMatch<MCRoom>> getRoomsMatching(ResourceLocation recipeId) {
        @NotNull TownFlagBlockEntity t = unsafeGetTown();
        if (SpecialQuests.TOWN_GATE.equals(recipeId)) {
            return getWelcomeMatMetaRooms(t);
        }
        if (SpecialQuests.TOWN_FLAG.equals(recipeId)) {
            return ImmutableList.of(getFlagMetaRoom(t));
        }

        return roomsMap.getRoomsMatching(recipeId);
    }

    private RoomRecipeMatch<MCRoom> getFlagMetaRoom(TownFlagBlockEntity t) {
        MCRoom mcRoom = Spaces.metaRoomAround(t.getTownFlagBasePos(), Config.META_ROOM_DIAMETER.get());
        ImmutableMap<BlockPos, Block> blocksInRoom = RecipeDetection.getBlocksInRoom(t.getServerLevel(), mcRoom, false);
        ResourceLocation questId = SpecialQuests.TOWN_FLAG;
        return new RoomRecipeMatch<>(mcRoom, questId, blocksInRoom.entrySet());
    }

    @NotNull
    private List<RoomRecipeMatch<MCRoom>> getWelcomeMatMetaRooms(@NotNull TownFlagBlockEntity t) {
        // TODO: Cache these
        Function<MCRoom, ImmutableMap<BlockPos, Block>> fn = room -> RecipeDetection.getBlocksInRoom(
                t.getServerLevel(),
                room,
                false
        );
        return t.getWelcomeMats()
                .stream()
                .map(p -> Spaces.metaRoomAround(p, Config.META_ROOM_DIAMETER.get()))
                .map(v -> new RoomRecipeMatch<>(
                        v, SpecialQuests.TOWN_GATE, fn.apply(v)
                                                      .entrySet()))
                .toList();
    }

    @Override
    public void roomRecipeCreated(
            MCRoom mcRoom,
            RoomRecipeMatch<MCRoom> mcRoomRoomRecipeMatch
    ) {
        @NotNull TownFlagBlockEntity t = unsafeGetTown();
        t.roomRecipeCreated(mcRoom, mcRoomRoomRecipeMatch);
    }

    @Override
    public void roomRecipeChanged(
            MCRoom mcRoom,
            RoomRecipeMatch<MCRoom> mcRoomRoomRecipeMatch,
            MCRoom room1,
            RoomRecipeMatch<MCRoom> key1
    ) {
        @NotNull TownFlagBlockEntity t = unsafeGetTown();
        t.roomRecipeChanged(mcRoom, mcRoomRoomRecipeMatch, room1, key1);
    }

    @Override
    public void roomRecipeDestroyed(
            MCRoom mcRoom,
            RoomRecipeMatch<MCRoom> mcRoomRoomRecipeMatch
    ) {
        @NotNull TownFlagBlockEntity t = unsafeGetTown();
        t.roomRecipeDestroyed(mcRoom, mcRoomRoomRecipeMatch);
    }

    @Override
    public TownFlagBlockEntity get() {
        return unsafeGetTown();
    }

    public TownRoomsMap getRegisteredRooms() {
        return roomsMap;
    }

    void tick(
            ServerLevel sl,
            BlockPos flagPos
    ) {
        roomsMap.tick(sl, flagPos);
    }

    public ImmutableList<MCRoom> getAllRoomsIncludingMeta() {
        ImmutableList.Builder<MCRoom> b = ImmutableList.builder();
        b.addAll(roomsMap.getAllRooms());
        assert flagMetaRoom != null;
        b.add(flagMetaRoom);
        @NotNull TownFlagBlockEntity t = unsafeGetTown();
        getWelcomeMatMetaRooms(t).forEach(v -> b.add(v.room));
        return b.build();
    }

    public Collection<MCRoom> getFarms() {
        return roomsMap.getFarms();
    }

    @Override
    public Collection<BlockPos> findMatchedRecipeBlocks(TownInterface.MatchRecipe mr) {
        ImmutableList.Builder<BlockPos> b = ImmutableList.builder();
        for (RoomRecipeMatch<MCRoom> i : roomsMap.getAllMatches()) {
            for (Map.Entry<BlockPos, Block> j : i.getContainedBlocks()
                                                 .entrySet()) {
                if (mr.doesMatch(j.getValue())) {
                    b.add(j.getKey());
                }
            }
        }
        return b.build();
    }

    boolean hasEnoughBeds(long numVillagers) {
        // TODO: This returns false positives if called before entities have been loaded from tile data
        long beds = roomsMap.getAllMatches()
                            .stream()
                            .flatMap(v -> v.getContainedBlocks()
                                           .values()
                                           .stream())
                            .filter(v -> Ingredient.of(ItemTags.BEDS)
                                                   .test(new ItemStack(v.asItem())))
                            .count();
        if (beds == 0 && numVillagers == 0) {
            return false;
        }
        return (beds / 2) >= numVillagers;
    }

    @Override
    public Collection<RoomRecipeMatch<MCRoom>> getMatches() {
        return this.roomsMap.getAllMatches();
    }

    public void registerDoor(BlockPos clickedPos) {
        @NotNull TownFlagBlockEntity t = unsafeGetTown();
        roomsMap.registerDoor(Positions.FromBlockPos(clickedPos), clickedPos.getY() - t.getY());
        t.setChanged();
    }

    @Override
    public Supplier<Boolean> getDebugTaskForAllDoors() {
        // TODO: Finish implementing this
        @NotNull TownFlagBlockEntity t = unsafeGetTown();
        ImmutableSet<TownPosition> registeredDoors = t.getRoomHandle()
                                                      .getAllRegisteredDoors();

        Map<Integer, Collection<Position>> doorsAtLevel = new HashMap<>();

        registeredDoors.forEach(dp ->
                doorsAtLevel.computeIfAbsent(dp.scanLevel, k -> new ArrayList<>())
                            .add(dp.toPosition())
        );

        Map<Integer, ActiveRecipes<MCRoom, RoomRecipeMatch<MCRoom>>> recipesAtLevel = new HashMap<>();
        doorsAtLevel.keySet()
                    .forEach(
                            scanLevel -> {
                                ActiveRecipes<MCRoom, RoomRecipeMatch<MCRoom>> value = new ActiveRecipes<>();
                                value.addChangeListener( // TODO: Update RR to make this optional
                                        new ActiveRecipes.ChangeListener<MCRoom, RoomRecipeMatch<MCRoom>>() {
                                            @Override
                                            public void roomRecipeCreated(
                                                    MCRoom room,
                                                    RoomRecipeMatch<MCRoom> mcRoomRoomRecipeMatch
                                            ) {

                                            }

                                            @Override
                                            public void roomRecipeChanged(
                                                    MCRoom room,
                                                    RoomRecipeMatch<MCRoom> mcRoomRoomRecipeMatch,
                                                    MCRoom room1,
                                                    RoomRecipeMatch<MCRoom> key1
                                            ) {

                                            }

                                            @Override
                                            public void roomRecipeDestroyed(
                                                    MCRoom room,
                                                    RoomRecipeMatch<MCRoom> mcRoomRoomRecipeMatch
                                            ) {

                                            }
                                        });
                                recipesAtLevel.put(scanLevel, value);
                            }
                    );

        final Map<Integer, ImmutableMap<Position, String>> artSink = new HashMap<>();

        MultiLevelRoomDetector ptr = initMLRD(t, recipesAtLevel, doorsAtLevel);
        ptr.setArtSink(artSink::put);
        return () -> {
            boolean done = ptr.proceed();
            if (!done) {
                return false;
            }
            // TODO: Make MultiLevelRD take a flightrecorder
//            flightRecorder.forEach(QT.FLAG_LOGGER::debug);
            HashMap<TownPosition, Result> results = new HashMap<TownPosition, Result>();
            artSink.forEach(
                    (scanLevel, arts) -> {
                        arts.forEach(
                                (k, v) -> results.compute(
                                        new TownPosition(k.x, k.z, scanLevel),
                                        (pos, cur) -> cur == null ? new Result(v, "NONE", "NONE") : new Result(
                                                v, cur.recipe, cur.room)
                                )
                        );
                        recipesAtLevel.get(scanLevel)
                                      .entrySet()
                                      .forEach(
                                              (k) -> results.compute(
                                                      new TownPosition(
                                                              k.getKey().doorPos.x, k.getKey().doorPos.z, scanLevel),
                                                      (pos, cur) -> {
                                                          String rec = String.format(
                                                                  "%s", k.getValue()
                                                                         .getRecipeID()
                                                          );
                                                          String rom = k.getValue().room.getSpace()
                                                                                        .toString();
                                                          return cur == null ? new Result(
                                                                  "NONE", rom, rec) : new Result(
                                                                  cur.debugArt,
                                                                  rom, rec
                                                          );
                                                      }
                                              )
                                      );
                    }
            );
            results.forEach(
                    (k, v) -> QT.FLAG_LOGGER.debug(
                            "At {} found recipe {} in room {} after scan:\n{}",
                            k.toPosition()
                             .getUIString(), v.recipe, v.room, v.debugArt
                    )
            );
            return true;
        };
    }

    private record Result(
            String debugArt,
            String room,
            String recipe
    ) {
    }

    @NotNull
    private static MultiLevelRoomDetector initMLRD(
            @NotNull TownFlagBlockEntity t,
            Map<Integer, ActiveRecipes<MCRoom, RoomRecipeMatch<MCRoom>>> recipesAtLevel,
            Map<Integer, Collection<Position>> doorsAtLevel
    ) {
        final int townY = t.getBlockPos()
                           .getY();
        return new MultiLevelRoomDetector(
                t.getServerLevel(),
                t.getY(),
                p -> WallDetection.IsWall(t.getServerLevel(), p.toPosition(), townY + p.scanLevel),
                p -> WallDetection.IsDoor(t.getServerLevel(), p.toPosition(), townY + p.scanLevel),
                (scanLevel, newRooms) -> {
                },
                recipesAtLevel::get,
                doorsAtLevel,
                true
        );
    }

    @Override
    public Supplier<Boolean> getDebugTaskForDoor(BlockPos clickedPos) {
        @NotNull TownFlagBlockEntity t = unsafeGetTown();
        LinkedBlockingQueue<String> flightRecorder = new LinkedBlockingQueue<>();
        Position clickedRRPos = Positions.FromBlockPos(clickedPos);
        final LevelRoomDetector d = new LevelRoomDetector(
                ImmutableList.of(clickedRRPos),
                Config.MAX_ROOM_DIMENSION.get(),
                Config.MAX_ROOM_SCAN_ITERATIONS.get(),
                p -> WallDetection.IsWall(t.getServerLevel(), p, clickedPos.getY()),
                true,
                flightRecorder
        );
        return () -> {
            @Nullable ImmutableMap<Position, Optional<Room>> done = d.proceed();
            if (done == null) {
                return false;
            }
            flightRecorder.forEach(QT.FLAG_LOGGER::debug);
            d.getDebugArt(true)
             .forEach(
                     (k, v) -> QT.FLAG_LOGGER.debug("Art for {}\n{}", k.getUIString(), v)
             );
            Optional<Room> room = done.get(clickedRRPos);
            QT.FLAG_LOGGER.debug("Room is {}", room);
            room.ifPresent(r -> {
                Optional<RoomRecipeMatch<MCRoom>> recipe = t.getRoomHandle()
                                                            .computeRecipe(new MCRoom(r.getDoorPos(),
                                                                    r.getSpaces(), clickedPos.getY()
                                                            ));
                QT.FLAG_LOGGER.debug("Recipe is {}", recipe);
            });
            if (!t.getRoomHandle()
                  .isDoorRegistered(clickedPos)) {
                QT.FLAG_LOGGER.warn("{} is not registered as a door", clickedPos);
            }
            return true;
        };
    }

    @Override
    public boolean isDoorRegistered(BlockPos clickedPos) {
        @NotNull TownFlagBlockEntity t = unsafeGetTown();
        return roomsMap.isDoorRegistered(Positions.FromBlockPos(clickedPos), clickedPos.getY() - t.getY());
    }

    public Optional<RoomRecipeMatch<MCRoom>> computeRecipe(
            MCRoom r
    ) {
        @NotNull TownFlagBlockEntity t = unsafeGetTown();
        return roomsMap.computeRecipe(t.getServerLevel(), r, r.yCoord - t.getY());
    }

    @Override
    public ImmutableSet<TownPosition> getAllRegisteredDoors() {
        return roomsMap.getAllRegisteredDoors();
    }

    public void registerFenceGate(BlockPos clickedPos) {
        @NotNull TownFlagBlockEntity t = unsafeGetTown();
        roomsMap.registerFenceGate(Positions.FromBlockPos(clickedPos), clickedPos.getY() - t.getY());
        t.setChanged();
    }
}
