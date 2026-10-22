package ca.bradj.questown.town.entity;

import ca.bradj.questown.QT;
import ca.bradj.questown.Questown;
import ca.bradj.questown.blocks.PlateBlock;
import ca.bradj.questown.blocks.RoomBlock;
import ca.bradj.questown.blocks.entity.BlockAsRoomEntity;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.core.UtilClean;
import ca.bradj.questown.core.init.BlocksInit;
import ca.bradj.questown.roomrecipes.Matches;
import ca.bradj.questown.roomrecipes.Spaces;
import ca.bradj.questown.town.UnsafeTown;
import ca.bradj.questown.town.WallDetection;
import ca.bradj.questown.town.special.SpecialQuests;
import ca.bradj.questown.town.interfaces.RoomsHolder;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.rooms.MultiLevelRoomDetector;
import ca.bradj.questown.town.rooms.TownPosition;
import ca.bradj.questown.town.rooms.TownRoomsMap;
import ca.bradj.questown.town.special.SpecialQuests;
import ca.bradj.roomrecipes.adapter.Positions;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatches;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class TownRoomsHandle implements RoomsHolder, Supplier<TownFlagBlockEntity> {

    private final TownRoomsMap roomsMap = new TownRoomsMap();
    private final UnsafeTown town = new UnsafeTown(TownRoomsHandle.class);
    @Nullable
    private MCRoom flagMetaRoom;
    private final Map<ResourceLocation, List<BlockPos>> roomBlocks = new HashMap<>();

    public void initializeNew(TownFlagBlockEntity t) {
        this.town.initialize(t);
        this.flagMetaRoom = Spaces.metaRoomAround(t.getBlockPos(), 2);
        roomsMap.initializeNew(t);
        roomsMap.addRecipeListener(t.quests);
        roomsMap.addRecipeListener(t);
    }

    @Override
    public Collection<RoomRecipeMatch<MCRoom>> getRoomsMatching(ResourceLocation recipeId) {
        @NotNull TownFlagBlockEntity t = town.getUnsafe();
        Optional<ResourceLocation> blockRoom = BlockAsRoomEntity.ALL.stream().map(Supplier::get)
                                                                    .map(RoomBlock::getRoomId).filter(recipeId::equals)
                                                                    .findFirst();
        if (blockRoom.isPresent()) {
            return getBlockMetaRooms(t, blockRoom.get());
        }
        if (SpecialQuests.TOWN_GATE.equals(recipeId)) {
            java.util.Collection<RoomRecipeMatch<MCRoom>> gateOut = getWelcomeMatMetaRooms(t);
            return gateOut;
        }
        if (SpecialQuests.JOB_BOARD.equals(recipeId)) {
            // The board quest's wanted id is the room recipe questown:job_board, but a placed
            // board registers as a synthetic block room under block_room/<board block id>
            // (RoomBlock.getRoomId), which the fast path above can never equal. Without this
            // branch, a board placed outside any door-registered room is invisible to the
            // per-tick backfill (the fallback below only scans door-registered rooms) and the
            // quest stalls ACTIVE forever - 2026-10-14 live playtest: the board was placed
            // "near the flag", exactly as the quest text says. Maps the quest id to the
            // board's registered block-room key, the way TOWN_GATE maps to the welcome mat.
            // (The event path - roomRecipeCreated's top match - can also miss it: the board's
            // meta room ties at equal cost between the real recipe and the synthetic
            // block_room recipe, and the winner is stream order, not merit.)
            return getBlockMetaRooms(t, RoomBlock.getRoomId(
                    (RoomBlock) BlocksInit.JOB_BOARD_BLOCK.get()));
        }
        if (SpecialQuests.TOWN_FLAG.equals(recipeId)) {
            return ImmutableList.of(getFlagMetaRoom(t));
        }
        if (SpecialQuests.FARM.equals(recipeId)) {
            return roomsMap.getFarms().stream().map(v -> {
                Function<BlockPos, BlockState> getBs = bp -> getServerLevel(town.getUnsafe()).getBlockState(bp);
                ImmutableMap<BlockPos, Block> b = RecipeDetection.getBlocksInRoomV2(
                        bp -> getBs.apply(bp).getBlock(),
                        new MCRoom(v.getDoorPos(), v.getSpaces(), v.yCoord),
                        false
                );
                return new RoomRecipeMatch<>(v, ImmutableList.of(recipeId), b.entrySet());
            }).toList();
        }

        return roomsMap.getRoomsMatching(recipeId);
    }

    @Override
    public void registerBlockAsRoom(
            ResourceLocation blockId,
            BlockPos pos
    ) {
        UtilClean.addOrInitialize(roomBlocks, blockId, pos, ArrayList::new);
        QT.FLAG_LOGGER.debug("Registered block-room at {}: {}", pos, blockId);
        TownFlagBlockEntity t = town.getUnsafe();
        t.subBlocks.register(pos);
        MCRoom room = Spaces.metaRoomAround(pos, Config.META_ROOM_DIAMETER.get());
        Map<BlockPos, Block> blocks = RecipeDetection.getBlocksInRoom(town.getServerLevelUnsafe(), room, false);
        // No broadcast here: broadcastBlockRoomCreated below is the one player-facing
        // "Created X" line (its match carries the placed block, not the meta-room's
        // surroundings). The legacy double-broadcast is messages-print-twice.md.
        t.roomRecipeCreated(room, new RoomRecipeMatches<>(room, ImmutableList.of(blockId), blocks.entrySet()), false);
        t.setChanged();
        broadcastBlockRoomCreated(blockId, pos, t);
    }

    private static void broadcastBlockRoomCreated(
            ResourceLocation blockId,
            BlockPos pos,
            TownFlagBlockEntity t
    ) {
        MCRoom room = Spaces.metaRoomAround(pos, 1);
        ImmutableMap<BlockPos, Block> blocks = ImmutableMap.of(
                pos, getBlockRoomBlock(pos, t)
        );
        RoomRecipeMatch<MCRoom> m = new RoomRecipeMatches<>(room, ImmutableList.of(blockId), blocks.entrySet());
        t.blockRoomCreated(room, m, pos);
    }

    private static @NotNull Block getBlockRoomBlock(
            BlockPos pos,
            TownFlagBlockEntity t
    ) {
        // We should be able to trust that the block state will not be null
        // because we are literally registering a "block room" at this position.
        // By necessity, that should mean the block state is not null
        //noinspection DataFlowIssue
        return t.getServerLevel().getBlockState(pos).getBlock();
    }

    private Collection<RoomRecipeMatch<MCRoom>> getBlockMetaRooms(
            @NotNull TownFlagBlockEntity t,
            @Nullable ResourceLocation resourceLocation
    ) {
        ImmutableList.Builder<RoomRecipeMatch<MCRoom>> b = ImmutableList.builder();
        Stream<Map.Entry<ResourceLocation, List<BlockPos>>> e = roomBlocks.entrySet().stream();
        if (resourceLocation != null) {
            List<BlockPos> blocksInRoom = UtilClean.getOrDefault(roomBlocks, resourceLocation, ImmutableList.of());
            e = blocksInRoom.stream().map(pos -> Map.entry(resourceLocation, ImmutableList.of(pos)));
        }
        e.forEach(ee -> {
            for (BlockPos p : ee.getValue()) {
                MCRoom mcRoom = Spaces.metaRoomAround(p, Config.META_ROOM_DIAMETER.get());
                ImmutableMap<BlockPos, Block> blocksInRoom = RecipeDetection.getBlocksInRoom(
                        getServerLevel(t),
                        mcRoom,
                        false
                );
                b.add(new RoomRecipeMatch<>(mcRoom, ImmutableList.of(ee.getKey()), blocksInRoom.entrySet()));
            }
        });
        return b.build();
    }

    private RoomRecipeMatch<MCRoom> getFlagMetaRoom(TownFlagBlockEntity t) {
        MCRoom mcRoom = Spaces.metaRoomAround(t.getTownFlagBasePos(), Config.META_ROOM_DIAMETER.get());
        ImmutableMap<BlockPos, Block> blocksInRoom = RecipeDetection.getBlocksInRoom(getServerLevel(t), mcRoom, false);
        ResourceLocation questId = SpecialQuests.TOWN_FLAG;
        return new RoomRecipeMatch<>(mcRoom, ImmutableList.of(questId), blocksInRoom.entrySet());
    }

    @SuppressWarnings("DataFlowIssue") // We can be fairly confident that this will not return null
    private static @NotNull ServerLevel getServerLevel(TownFlagBlockEntity t) {
        return t.getServerLevel();
    }

    @NotNull
    private List<RoomRecipeMatch<MCRoom>> getWelcomeMatMetaRooms(@NotNull TownFlagBlockEntity t) {
        // TODO: Cache these
        Function<MCRoom, ImmutableMap<BlockPos, Block>> fn = room -> RecipeDetection.getBlocksInRoom(
                getServerLevel(t),
                room,
                false
        );
        return t.getWelcomeMats().stream().map(p -> Spaces.metaRoomAround(p, Config.META_ROOM_DIAMETER.get()))
                .map(v -> new RoomRecipeMatch<>(v, ImmutableList.of(SpecialQuests.TOWN_GATE), fn.apply(v).entrySet()))
                .toList();
    }

    @Override
    public TownFlagBlockEntity get() {
        return town.getUnsafe();
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

    public ImmutableList<MCRoom> getAllRoomsIncludingMetaAndFarms() {
        ImmutableList.Builder<MCRoom> b = ImmutableList.builder();
        b.addAll(roomsMap.getAllRooms());
        b.addAll(roomsMap.getFarms());
        assert flagMetaRoom != null;
        b.add(flagMetaRoom);
        @NotNull TownFlagBlockEntity t = town.getUnsafe();
        getWelcomeMatMetaRooms(t).forEach(v -> b.add(v.room));
        getBlockMetaRooms(t, null).forEach(v -> b.add(v.room));
        return b.build();
    }

    public Collection<MCRoom> getFarms() {
        return roomsMap.getFarms();
    }

    @Override
    public Collection<BlockPos> findMatchedRecipeBlocks(TownInterface.MatchRecipe mr) {
        ImmutableList.Builder<BlockPos> b = ImmutableList.builder();
        for (RoomRecipeMatch<MCRoom> i : roomsMap.getAllMatches(x -> true)) {
            for (Map.Entry<BlockPos, Block> j : i.getContainedBlocks().entrySet()) {
                if (mr.doesMatch(j.getValue())) {
                    b.add(j.getKey());
                }
            }
        }
        return b.build();
    }

    private long bedBlockCount() {
        return roomsMap.getAllMatches(v -> true).stream().flatMap(v -> v.getContainedBlocks().values().stream())
                       .filter(v -> Ingredient.of(ItemTags.BEDS).test(new ItemStack(v.asItem()))).count();
    }

    boolean hasEnoughBeds(long numVillagers) {
        // TODO: This returns false positives if called before entities have been loaded from tile data
        long beds = bedBlockCount();
        if (beds == 0 && numVillagers == 0) {
            return false;
        }
        return (beds / 2) >= numVillagers;
    }

    /**
     * The bed shortfall: how many more <em>complete</em> beds (2 blocks each) the town needs to
     * reach {@code beds/2 >= numVillagers}. 0 when the town already has enough. The quest
     * garden uses this to post enough bedroom quests to cover the shortfall (day-1 playtest:
     * the town was under-bedded - it posted at most one bed quest per batch).
     */
    int missingBeds(long numVillagers) {
        long completeBeds = bedBlockCount() / 2;
        return (int) Math.max(0L, numVillagers - completeBeds);
    }

    @Override
    public Collection<RoomRecipeMatch<MCRoom>> getMatches(Predicate<RoomRecipeMatch<MCRoom>> include) {
        Collection<RoomRecipeMatch<MCRoom>> matches;
        // 2026-10-06 flake fix: the TOWN_GATE match normally comes from the gate DOOR ROOM's
        // recipe, captured by the room-detection pump at door registration. In ~half of seeded
        // worlds the pump completed before the welcome mat was placed, so the door room never
        // matches special_quest.town_gate and leavers stay in COLLECTING_SUPPLIES forever
        // (their supplies scan - Containers.get via this method - has no candidate container).
        // The welcome mat defines its own meta room (getWelcomeMatMetaRooms); when no room-map
        // entry carries the TOWN_GATE recipe, add it so the mat's room (and the seeded mat chest
        // inside it) is visible to the selector regardless of detection ordering.
        java.util.List<RoomRecipeMatch<MCRoom>> result = new java.util.ArrayList<>(roomsMap.getAllMatches(include));
        boolean hasGate = result.stream().anyMatch(m -> m.anyMatch(SpecialQuests.TOWN_GATE));
        if (!hasGate) {
            java.util.List<RoomRecipeMatch<MCRoom>> matRooms = getWelcomeMatMetaRooms(town.getUnsafe());
            matRooms.stream()
                    .filter(include)
                    .forEach(result::add);
        }
        return result;
    }

    public void registerDoor(BlockPos clickedPos) {
        @NotNull TownFlagBlockEntity t = town.getUnsafe();
        roomsMap.registerDoor(Positions.FromBlockPos(clickedPos), clickedPos.getY() - t.getY());
    }

    @Override
    public void deregisterDoor(Deregistration d) {
        @NotNull TownFlagBlockEntity t = town.getUnsafe();
        Position doorPos = Positions.FromBlockPos(d.doorPos());
        roomsMap.deRegisterDoor(doorPos, d.doorPos().getY() - t.getY(), d.reason());
        t.setChanged();
    }

    @Override
    public Supplier<Boolean> getDebugTaskForAllDoors() {
        // TODO: Finish implementing this
        @NotNull TownFlagBlockEntity t = town.getUnsafe();
        ImmutableSet<TownPosition> registeredDoors = t.getRoomHandle().getAllRegisteredDoors();

        Map<Integer, Collection<Position>> doorsAtLevel = new HashMap<>();

        registeredDoors.forEach(dp -> doorsAtLevel.computeIfAbsent(dp.scanLevel, k -> new ArrayList<>())
                                                  .add(dp.toPosition()));

        Map<Integer, ActiveRecipes<MCRoom, RoomRecipeMatch<MCRoom>>> recipesAtLevel = new HashMap<>();
        doorsAtLevel.keySet().forEach(scanLevel -> {
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
        });

        final Map<Integer, ImmutableMap<Position, String>> artSink = new HashMap<>();

        MultiLevelRoomDetector ptr = initMLRD(t, recipesAtLevel, doorsAtLevel);
        ptr.setArtSink(artSink::put);
        return () -> {
            boolean done = ptr.proceed(i -> null);
            if (!done) {
                return false;
            }
            // TODO: Make MultiLevelRD take a flightrecorder
//            flightRecorder.forEach(QT.FLAG_LOGGER::debug);
            HashMap<TownPosition, Result> results = new HashMap<TownPosition, Result>();
            artSink.forEach((scanLevel, arts) -> {
                arts.forEach((k, v) -> results.compute(
                        new TownPosition(k.x, k.z, scanLevel),
                        (pos, cur) -> cur == null ? new Result(v, "NONE", "NONE") : new Result(v, cur.recipe, cur.room)
                ));
                recipesAtLevel.get(scanLevel).entrySet().forEach((k) -> results.compute(
                        new TownPosition(k.getKey().doorPos.x, k.getKey().doorPos.z, scanLevel), (pos, cur) -> {
                            String rec = Matches.toString(k.getValue());
                            String rom = k.getValue().room.getSpace().toString();
                            return cur == null ? new Result("NONE", rom, rec) : new Result(cur.debugArt, rom, rec);
                        }
                ));
            });
            results.forEach((k, v) -> QT.FLAG_LOGGER.debug(
                    "At {} found recipe {} in room {} after scan:\n{}",
                    k.toPosition().getUIString(),
                    v.recipe,
                    v.room,
                    v.debugArt
            ));
            return true;
        };
    }

    public void handleMorning() {
        resetDiningRooms();
        resetBedrooms();
    }


    private void resetDiningRooms() {
        Collection<RoomRecipeMatch<MCRoom>> diningRooms = getMatches(m -> m.anyMatch(Questown.ResourceLocation(
                "dining_room")));
        for (RoomRecipeMatch<MCRoom> diningRoom : diningRooms) {
            for (Map.Entry<BlockPos, Block> e : diningRoom.getContainedBlocks().entrySet()) {
                if (!(e.getValue() instanceof PlateBlock)) {
                    continue;
                }
                QT.FLAG_LOGGER.debug("Resetting plate claim and state at {}", e.getKey());
                town.getUnsafe().jobHandle.clearClaim(e.getKey());
                town.getUnsafe().jobHandle.clearState(e.getKey());
            }
        }
    }

    private void resetBedrooms() {
        Collection<RoomRecipeMatch<MCRoom>> rooms = getMatches(m -> m.anyMatch(Questown.ResourceLocation("bedroom")));
        for (RoomRecipeMatch<MCRoom> room : rooms) {
            for (Map.Entry<BlockPos, Block> e : room.getContainedBlocks().entrySet()) {
                ServerLevel sl = town.getServerLevelUnsafe();
                BlockState oldBs = sl.getBlockState(e.getKey());
                if (!(oldBs.getBlock() instanceof BedBlock bb)) {
                    continue;
                }
                @SuppressWarnings("AccessStaticViaInstance") BlockState newBs = oldBs.setValue(bb.OCCUPIED, false);
                List<LivingEntity> list = sl.getEntitiesOfClass(
                        LivingEntity.class,
                        new AABB(e.getKey()),
                        LivingEntity::isSleeping
                );
                sl.setBlockAndUpdate(e.getKey(), newBs);
                for (LivingEntity villager : list) {
                    villager.stopSleeping();
                }
            }
        }
    }

    public ImmutableList<BlockPos> blockRooms() {
        return roomBlocks.values().stream().flatMap(Collection::stream).collect(ImmutableList.toImmutableList());
    }

    private record Result(String debugArt, String room, String recipe) {
    }

    @NotNull
    private static MultiLevelRoomDetector initMLRD(
            @NotNull TownFlagBlockEntity t,
            Map<Integer, ActiveRecipes<MCRoom, RoomRecipeMatch<MCRoom>>> recipesAtLevel,
            Map<Integer, Collection<Position>> doorsAtLevel
    ) {
        final int townY = t.getBlockPos().getY();
        return new MultiLevelRoomDetector(
                getServerLevel(t),
                t.getY(),
                p -> WallDetection.IsWall(getServerLevel(t), p.toPosition(), townY + p.scanLevel),
                p -> WallDetection.IsDoor(getServerLevel(t), p.toPosition(), townY + p.scanLevel),
                (scanLevel, newRooms) -> {
                },
                recipesAtLevel::get,
                doorsAtLevel,
                true
        );
    }

    @Override
    public Supplier<Boolean> getDebugTaskForDoor(BlockPos clickedPos) {
        @NotNull TownFlagBlockEntity t = town.getUnsafe();
        LinkedBlockingQueue<String> flightRecorder = new LinkedBlockingQueue<>();
        Position clickedRRPos = Positions.FromBlockPos(clickedPos);
        final LevelRoomDetector d = new LevelRoomDetector(
                ImmutableList.of(clickedRRPos),
                Config.MAX_ROOM_DIMENSION.get(),
                Config.MAX_ROOM_SCAN_ITERATIONS.get(),
                p -> WallDetection.IsWall(getServerLevel(t), p, clickedPos.getY()),
                true,
                flightRecorder::add
        );
        return () -> {
            @Nullable ImmutableMap<Position, Optional<Room>> done = d.proceed();
            if (done == null) {
                return false;
            }
            flightRecorder.forEach(QT.FLAG_LOGGER::debug);
            d.getDebugArt(true).forEach((k, v) -> QT.FLAG_LOGGER.debug("Art for {}\n{}", k.getUIString(), v));
            Optional<Room> room = UtilClean.getOrDefault(done, clickedRRPos, Optional.empty());
            QT.FLAG_LOGGER.debug("Room is {}", room);
            room.ifPresent(r -> {
                Optional<RoomRecipeMatches<MCRoom>> recipe = t.getRoomHandle().computeRecipe(new MCRoom(
                        r.getDoorPos(),
                        r.getSpaces(),
                        clickedPos.getY()
                ));
                QT.FLAG_LOGGER.debug("Recipe is {}", recipe);
            });
            if (!t.getRoomHandle().isDoorRegistered(clickedPos)) {
                QT.FLAG_LOGGER.warn("{} is not registered as a door", clickedPos);
            }
            return true;
        };
    }

    @Override
    public boolean isDoorRegistered(BlockPos clickedPos) {
        @NotNull TownFlagBlockEntity t = town.getUnsafe();
        return roomsMap.isDoorRegistered(Positions.FromBlockPos(clickedPos), clickedPos.getY() - t.getY());
    }

    public Optional<RoomRecipeMatches<MCRoom>> computeRecipe(
            MCRoom r
    ) {
        @NotNull TownFlagBlockEntity t = town.getUnsafe();
        return roomsMap.computeRecipe(getServerLevel(t), r);
    }

    @Override
    public ImmutableSet<TownPosition> getAllRegisteredDoors() {
        return roomsMap.getAllRegisteredDoors();
    }

    public void registerFenceGate(BlockPos clickedPos) {
        @NotNull TownFlagBlockEntity t = town.getUnsafe();
        roomsMap.registerFenceGate(Positions.FromBlockPos(clickedPos), clickedPos.getY() - t.getY());
        t.setChanged();
    }
}
