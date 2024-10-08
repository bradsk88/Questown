package ca.bradj.questown.town.rooms;

import ca.bradj.questown.QT;
import ca.bradj.questown.Questown;
import ca.bradj.questown.blocks.FalseDoorBlock;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.town.TownFlagBlockEntity;
import ca.bradj.questown.town.TownRooms;
import ca.bradj.questown.town.WallDetection;
import ca.bradj.questown.town.special.SpecialQuests;
import ca.bradj.roomrecipes.adapter.Positions;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.roomrecipes.core.Room;
import ca.bradj.roomrecipes.core.space.Position;
import ca.bradj.roomrecipes.logic.LevelRoomDetection;
import ca.bradj.roomrecipes.recipes.ActiveRecipes;
import ca.bradj.roomrecipes.recipes.RecipeDetection;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.Tags;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TownRoomsMap implements TownRooms.RecipeRoomChangeListener {
    private final Map<Integer, TownRooms> activeRooms = new HashMap<>();
    private final Map<Integer, TownRooms> activeFarms = new HashMap<>();
    private final Map<Integer, ActiveRecipes<MCRoom, RoomRecipeMatch<MCRoom>>> activeRecipes = new HashMap<>();
    private final ArrayList<Integer> times = new ArrayList<>();
    private @Nullable MultiLevelRoomDetector pendingRooms;
    private List<ActiveRecipes.ChangeListener<MCRoom, RoomRecipeMatch<MCRoom>>> recipeListeners = new ArrayList<>();
    private @Nullable TownFlagBlockEntity town;
    private final Map<TownPosition, Integer> doorsToDrop = new HashMap<>();

    Set<TownPosition> getRegisteredDoors() {
        return registeredDoors;
    }

    public Set<TownPosition> getRegisteredGates() {
        return registeredFenceGates;
    }

    private final Set<TownPosition> registeredDoors = new HashSet<>();

    private final Set<TownPosition> registeredFenceGates = new HashSet<>();

    private void updateActiveFarms(
            ServerLevel level,
            int scanLevel,
            int scanY,
            Set<Position> registeredDoors
    ) {
        TownRooms ars = getOrCreateFarms(scanLevel);

        ImmutableMap<Position, Optional<Room>> rooms = LevelRoomDetection.findRooms(
                registeredDoors, 20, (Position p) -> isFence(level, Positions.ToBlock(p, scanY))
        );
        Function<Room, MCRoom> fn = z -> new MCRoom(
                z.getDoorPos(),
                z.getSpaces(),
                scanY - 1
        );
        List<AbstractMap.SimpleEntry<Position, Optional<MCRoom>>> array = rooms
                .entrySet()
                .stream()
                .map(v -> new AbstractMap.SimpleEntry<>(
                        v.getKey(),
                        v.getValue().map(fn)
                ))
                .toList();
        ImmutableMap<Position, Optional<MCRoom>> mcRooms = ImmutableMap.copyOf(array);
        ars.update(mcRooms);
    }

    private static boolean isFence(
            ServerLevel level,
            BlockPos bPos
    ) {
        BlockState bs = level.getBlockState(bPos);
        return Ingredient.of(Tags.Items.FENCES)
                         .test(new ItemStack(bs.getBlock()
                                               .asItem(), 1)) ||
                Ingredient.of(Tags.Items.FENCE_GATES)
                          .test(new ItemStack(bs.getBlock()
                                                .asItem(), 1));
    }

    private TownRooms getOrCreateRooms(int scanLevel) {
        if (!activeRecipes.containsKey(scanLevel)) {
            ActiveRecipes<MCRoom, RoomRecipeMatch<MCRoom>> v = new ActiveRecipes<>();
            activeRecipes.put(scanLevel, v);
            TownRoomsMap self = this;
            v.addChangeListener(new ActiveRecipes.ChangeListener<>() {
                @Override
                public void roomRecipeCreated(
                        MCRoom room,
                        RoomRecipeMatch<MCRoom> mcRoomRoomRecipeMatch
                ) {
                    self.recipeListeners.forEach(v -> v.roomRecipeCreated(room, mcRoomRoomRecipeMatch));
                }

                @Override
                public void roomRecipeChanged(
                        MCRoom room,
                        RoomRecipeMatch<MCRoom> mcRoomRoomRecipeMatch,
                        MCRoom room1,
                        RoomRecipeMatch<MCRoom> key1
                ) {
                    self.recipeListeners.forEach(v -> v.roomRecipeChanged(room, mcRoomRoomRecipeMatch, room1, key1));
                }

                @Override
                public void roomRecipeDestroyed(
                        MCRoom room,
                        RoomRecipeMatch<MCRoom> mcRoomRoomRecipeMatch
                ) {
                    self.recipeListeners.forEach(v -> v.roomRecipeDestroyed(room, mcRoomRoomRecipeMatch));
                }
            });
        }

        if (!activeRooms.containsKey(scanLevel)) {
            TownRooms v = new TownRooms(
                    scanLevel,
                    this::unsafeGetTown
            ); // TODO: Consider using listener instead of passing entity
            activeRooms.put(scanLevel, v);
            v.addRecipeRoomChangeListener(this);
        }

        return activeRooms.get(scanLevel);
    }

    private TownRooms getOrCreateFarms(int scanLevel) {
        if (!activeFarms.containsKey(scanLevel)) {
            TownRooms v = new TownRooms(
                    scanLevel,
                    this::unsafeGetTown
            ) {
                @Override
                protected Optional<RoomRecipeMatch<MCRoom>> getActiveRecipe(
                        ServerLevel entity,
                        MCRoom room
                ) {
                    ImmutableMap<BlockPos, Block> blocks = RecipeDetection.getBlocksInRoom(entity, room, false);
                    return Optional.of(new RoomRecipeMatch<>(
                            room, SpecialQuests.FARM, blocks.entrySet()
                    ));
                }
            };
            v.addRecipeRoomChangeListener(this);
            activeFarms.put(scanLevel, v);
        }

        return activeFarms.get(scanLevel);
    }

    public void tick(
            ServerLevel level,
            BlockPos flagPos
    ) {
        if (town == null) {
            throw new IllegalStateException("TownRoomsMap was never initialized");
        }
        if (pendingRooms != null) {
            long start = System.currentTimeMillis();
            boolean finished = pendingRooms.proceed(activeRooms::get);
            if (finished) {
                this.pendingRooms = null;
            }
            profileTick("PTR.proceed", start);
            return;
        }

        registeredDoors.stream()
                       .filter(tp -> {
                           BlockPos bp = new BlockPos(tp.x, flagPos.getY() + tp.scanLevel, tp.z);
                           BlockState bs = level.getBlockState(bp);
                           return !(bs.getBlock() instanceof DoorBlock || bs.getBlock() instanceof FalseDoorBlock);
                       })
                       .toList()
                       .forEach(tp -> {
                           registeredDoors.remove(tp);
                           QT.FLAG_LOGGER.debug("Door was de-registered due to not existing anymore");
                       });

        Map<Integer, Collection<Position>> doorsAtLevel = new HashMap<>();

        registeredDoors.forEach(dp ->
                doorsAtLevel.computeIfAbsent(dp.scanLevel, k -> new ArrayList<>())
                            .add(dp.toPosition())
        );

        pendingRooms = new MultiLevelRoomDetector(
                level, flagPos.getY(),
                p -> WallDetection.IsWall(level, p.toPosition(), flagPos.getY() + p.scanLevel),
                p -> WallDetection.IsDoor(level, p.toPosition(), flagPos.getY() + p.scanLevel),
                (scanLevel, rooms) -> {
                    getOrCreateRooms(scanLevel).update(rooms);
                    registeredDoors.stream().filter(v -> rooms.values().stream()
                                                              .noneMatch(z -> z.isPresent() && v.toPosition()
                                                                                                .equals(z.get().doorPos)))
                                   .forEach(
                                           nonRoomDoor -> doorsToDrop.compute(new TownPosition(
                                                   nonRoomDoor.x,
                                                   nonRoomDoor.z,
                                                   scanLevel
                                           ), (door, ticks) -> ticks == null ? 1 : ticks + 1)
                                   );
                    dropDeadDoors(flagPos);
                },
                activeRecipes::get,
                ImmutableMap.copyOf(doorsAtLevel),
                false
        );

        long start = System.currentTimeMillis();

        for (int scanLev : registeredFenceGates.stream()
                                               .map(v -> v.scanLevel)
                                               .distinct()
                                               .toList()) {
            Set<Position> gatesAtLevel = registeredFenceGates.stream()
                                                             .filter(v -> v.scanLevel == scanLev)
                                                             .map(p -> new Position(p.x, p.z))
                                                             .collect(Collectors.toSet());
            int y1 = flagPos.offset(0, scanLev, 0)
                            .getY();
            // FIXME: Do the farms too
            updateActiveFarms(level, scanLev, y1, gatesAtLevel);
        }
        profileTick("queue+farm", start);
    }

    private void dropDeadDoors(BlockPos flagPos) {
        int ticksToKeepNonRoomDoors = 100;
        List<Map.Entry<TownPosition, Integer>> deadDoors = doorsToDrop.entrySet().stream()
                                                                      .filter(v -> v.getValue() > ticksToKeepNonRoomDoors)
                                                                      .toList();
        ImmutableList<Map.Entry<TownPosition, Integer>> snapshot = ImmutableList.copyOf(deadDoors);
        snapshot.forEach(entry -> {
            TownPosition deadDoor = entry.getKey();
            BlockPos p = new BlockPos(
                    deadDoor.x,
                    deadDoor.scanLevel + flagPos.getY(),
                    deadDoor.z
            );
            unsafeGetTown().getRoomHandle().deregisterDoor(p);
            QT.FLAG_LOGGER.info(
                    "De-registered door at {} because {} full town scans finished without finding a valid room",
                    Positions.FromBlockPos(p).getUIString(), ticksToKeepNonRoomDoors
            );
            doorsToDrop.remove(new TownPosition(p.getX(), p.getZ(), p.getY() - flagPos.getY()));
        });
    }

    private void profileTick(
            String prefix,
            long start
    ) {
        if (Config.TICK_SAMPLING_RATE.get() == 0) {
            return;
        }
        long end = System.currentTimeMillis();
        times.add((int) (end - start));

        if (times.size() > Config.TICK_SAMPLING_RATE.get()) {
            String msg = "[TownRoomsMap:{}] Average tick length: {}";
            OptionalDouble val = times.stream()
                                      .mapToInt(Integer::intValue)
                                      .average();
            if (val.isPresent() && val.getAsDouble() > 10) {
                QT.PROFILE_LOGGER.error(msg, prefix, val);
            } else if (val.isPresent() && val.getAsDouble() > 1) {
                QT.PROFILE_LOGGER.warn(msg, prefix, val);
            } else {
                QT.PROFILE_LOGGER.debug(msg, prefix, val);
            }
            times.clear();
        }
    }

    public void initializeNew(
            TownFlagBlockEntity owner
    ) {
        this.initialize(
                owner,
                ImmutableMap.of(),
                ImmutableList.of(),
                ImmutableList.of()
        );
    }

    public void initialize(
            TownFlagBlockEntity owner,
            Map<Integer, ActiveRecipes<MCRoom, RoomRecipeMatch<MCRoom>>> ars,
            ImmutableList<TownPosition> registeredDoors,
            ImmutableList<TownPosition> registeredFenceGates
    ) {
        if (!this.activeRecipes.isEmpty()) {
            throw new IllegalStateException("Double initialization");
        }
        this.activeRecipes.putAll(ars);
        for (ActiveRecipes<MCRoom, RoomRecipeMatch<MCRoom>> r : ars.values()) {
            r.addChangeListener(owner);
        }
        this.registeredDoors.addAll(registeredDoors);
        this.registeredFenceGates.addAll(registeredFenceGates);
        this.town = owner;
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

    public Collection<MCRoom> getAllRooms() {
        return this.activeRooms.values()
                               .stream()
                               .map(TownRooms::getAll)
                               .flatMap(Collection::stream)
                               .toList();
    }

    @Override
    public void updateRecipeForRoom(
            int scanLevel,
            MCRoom oldRoom,
            MCRoom newRoom,
            @Nullable RoomRecipeMatch resourceLocation
    ) {
        getOrCreateRooms(scanLevel);
        activeRecipes.get(scanLevel)
                     .update(oldRoom, newRoom, resourceLocation);
    }

    public int numRecipes() {
        return this.activeRecipes.size();
    }

    public Collection<RoomRecipeMatch<MCRoom>> getAllMatches(Predicate<RoomRecipeMatch<MCRoom>> include) {
        Stream<RoomRecipeMatch<MCRoom>> objectStream = this.activeRecipes
                .values()
                .stream()
                .map(ActiveRecipes::entrySet)
                .flatMap(v -> v.stream().map(Map.Entry::getValue))
                .filter(include);
        return objectStream.collect(Collectors.toSet());
    }

    public void registerDoor(
            Position p,
            int scanLevel
    ) {
        registeredDoors.add(new TownPosition(p.x, p.z, scanLevel));
        Questown.LOGGER.debug("Door was registered at x={}, z={}, scanLevel={}", p.x, p.z, scanLevel);
    }

    public void deRegisterDoor(
            Position p,
            int scanLevel
    ) {
        registeredDoors.remove(new TownPosition(p.x, p.z, scanLevel));
        Questown.LOGGER.debug("Door was de-registered at x={}, z={}, scanLevel={}", p.x, p.z, scanLevel);
    }

    public void registerFenceGate(
            Position p,
            int scanLevel
    ) {
        registeredFenceGates.add(new TownPosition(p.x, p.z, scanLevel));
        Questown.LOGGER.debug("Fence gate was registered at x={}, z={}, scanLevel={}", p.x, p.z, scanLevel);
    }

    public Collection<RoomRecipeMatch<MCRoom>> getRoomsMatching(ResourceLocation recipeId) {
        ImmutableList.Builder<RoomRecipeMatch<MCRoom>> b = ImmutableList.builder();
        for (TownPosition p : registeredDoors) {
            Position pz = new Position(p.x, p.z);
            TownRooms rooms = getOrCreateRooms(p.scanLevel);
            Optional<MCRoom> room = rooms.getAll()
                                         .stream()
                                         .filter(v -> v.getDoorPos()
                                                       .equals(pz))
                                         .findFirst();
            if (room.isEmpty()) {
                continue;
            }
            ActiveRecipes<MCRoom, RoomRecipeMatch<MCRoom>> recipes = activeRecipes.get(p.scanLevel);
            for (Map.Entry<MCRoom, RoomRecipeMatch<MCRoom>> m : recipes.entrySet()) {
                if (m.getKey()
                     .equals(room.get()) && m.getValue()
                                             .getRecipeID()
                                             .equals(recipeId)) {
                    b.add(m.getValue());
                }
            }
        }
        return b.build();
    }

    public Collection<MCRoom> getFarms() {
        return activeFarms.entrySet()
                          .stream()
                          .flatMap(v -> v.getValue()
                                         .getAll()
                                         .stream())
                          .toList();
    }

    public boolean isDoorRegistered(
            Position position,
            int y
    ) {
        return registeredDoors
                .stream()
                .anyMatch(v -> v.scanLevel == y && position.x == v.x && position.z == v.z);
    }

    public Optional<RoomRecipeMatch<MCRoom>> computeRecipe(
            ServerLevel serverLevel,
            MCRoom r,
            int scanLevel
    ) {
        return RecipeDetection.getActiveRecipe(serverLevel, r, getOrCreateRooms(scanLevel));
    }

    public ImmutableSet<TownPosition> getAllRegisteredDoors() {
        return ImmutableSet.copyOf(registeredDoors);
    }

    public void addRecipeListener(
            ActiveRecipes.ChangeListener<MCRoom, RoomRecipeMatch<MCRoom>> l
    ) {
        this.recipeListeners.add(l);
    }
}
