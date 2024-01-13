package ca.bradj.questown.town.rooms;

import ca.bradj.questown.Questown;
import ca.bradj.questown.blocks.FalseDoorBlock;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.logic.TownCycle;
import ca.bradj.questown.town.TownFlagBlockEntity;
import ca.bradj.questown.town.TownRooms;
import ca.bradj.questown.town.TownRoomsHandle;
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
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.Tags;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TownRoomsMap implements TownRooms.RecipeRoomChangeListener {
    private final Map<Integer, TownRooms> activeRooms = new HashMap<>();
    private final Map<Integer, TownRooms> activeFarms = new HashMap<>();
    private final Map<Integer, ActiveRecipes<MCRoom, RoomRecipeMatch<MCRoom>>> activeRecipes = new HashMap<>();
    private int scanLevel = 0;
    private int scanBuffer = 0;
    private TownRoomsHandle changeListener;
    private final ArrayList<Integer> times = new ArrayList<>();

    Set<TownPosition> getRegisteredDoors() {
        return registeredDoors;
    }

    public Set<TownPosition> getRegisteredGates() {
        return registeredFenceGates;
    }

    private final Set<TownPosition> registeredDoors = new HashSet<>();

    private final Set<TownPosition> registeredFenceGates = new HashSet<>();

    public TownRoomsMap(TownRoomsHandle entity) {
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

        if (rooms.isEmpty()) {
            return;
        }

        List<AbstractMap.SimpleEntry<Position, Optional<MCRoom>>> array = rooms.entrySet()
                .stream()
                .map(v -> new AbstractMap.SimpleEntry<>(
                        v.getKey(), v.getValue().map(z -> new MCRoom(
                        z.getDoorPos(), z.getSpaces(), scanY
                ))))
                .toList();
        ImmutableMap<Position, Optional<MCRoom>> mcRooms = ImmutableMap.copyOf(array);
        ars.update(mcRooms);

        ars.getAll().forEach(room -> {
            Optional<RoomRecipeMatch<MCRoom>> recipe = RecipeDetection.getActiveRecipe(
                    level,
                    room,
                    ars
            );
            ActiveRecipes<MCRoom, RoomRecipeMatch<MCRoom>> rs = activeRecipes.get(scanLevel);
            rs.update(room, room, recipe.orElse(null));
        });
    }

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
        List<AbstractMap.SimpleEntry<Position, Optional<MCRoom>>> array = rooms.entrySet()
                .stream()
                .map(v -> new AbstractMap.SimpleEntry<>(
                        v.getKey(), v.getValue().map(z -> new MCRoom(
                        z.getDoorPos(), z.getSpaces(), scanY
                ))))
                .toList();
        ImmutableMap<Position, Optional<MCRoom>> mcRooms = ImmutableMap.copyOf(array);
        ars.update(mcRooms);
    }

    private static boolean isFence(
            ServerLevel level,
            BlockPos bPos
    ) {
        BlockState bs = level.getBlockState(bPos);
        return Ingredient.of(Tags.Items.FENCES).test(new ItemStack(bs.getBlock().asItem(), 1)) ||
                Ingredient.of(Tags.Items.FENCE_GATES).test(new ItemStack(bs.getBlock().asItem(), 1));
    }

    private TownRooms getOrCreateRooms(int scanLevel) {
        if (!activeRecipes.containsKey(scanLevel)) {
            ActiveRecipes<MCRoom, RoomRecipeMatch<MCRoom>> v = new ActiveRecipes<>();
            activeRecipes.put(scanLevel, v);
            v.addChangeListener(changeListener);
        }

        if (!activeRooms.containsKey(scanLevel)) {
            TownRooms v = new TownRooms(
                    scanLevel,
                    changeListener
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
                    changeListener // TODO: Consider using listener instead of passing entity
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
            BlockPos blockPos
    ) {
        registeredDoors.stream()
                .filter(tp -> {
                    BlockPos bp = new BlockPos(tp.x, blockPos.getY() + tp.scanLevel, tp.z);
                    BlockState bs = level.getBlockState(bp);
                    return !(bs.getBlock() instanceof DoorBlock || bs.getBlock() instanceof FalseDoorBlock);
                })
                .toList()
                .forEach(tp -> {
                    registeredDoors.remove(tp);
                    Questown.LOGGER.debug("Door was de-registered due to not existing anymore");
                });

        long start = System.currentTimeMillis();
//        scanBuffer = (scanBuffer + 1) % 2;
//        if (scanBuffer == 0) {
        // TODO: Use a FIFO queue and only run one iteration (y level) per tick
        scanLevel = (scanLevel + 1) % 5;
//        }
        Position scanAroundPos = Positions.FromBlockPos(blockPos);
        Set<Position> doorsAtZero = registeredDoors.stream()
                .filter(v -> v.scanLevel == 0)
                .map(p -> new Position(p.x, p.z))
                .collect(Collectors.toSet());
        updateActiveRooms(level, scanAroundPos, 0, blockPos.getY(), doorsAtZero);

        if (scanLevel != 0) {
            Set<Position> doorsAtLevel = registeredDoors.stream()
                    .filter(v -> v.scanLevel == scanLevel)
                    .map(p -> new Position(p.x, p.z))
                    .collect(Collectors.toSet());
            int y = blockPos.offset(0, scanLevel, 0).getY();
            updateActiveRooms(level, scanAroundPos, scanLevel, y, doorsAtLevel);
        }

        for (int scanLev : registeredDoors.stream().map(v -> v.scanLevel).distinct().toList()) {
            if (scanLev == scanLevel || scanLev == 0) {
                continue;
            }
            Set<Position> doorsAtLevel = registeredDoors.stream()
                    .filter(v -> v.scanLevel == scanLev)
                    .map(p -> new Position(p.x, p.z))
                    .collect(Collectors.toSet());
            int y1 = blockPos.offset(0, scanLev, 0).getY();
            updateActiveRooms(level, null, scanLev, y1, doorsAtLevel);
        }

        for (int scanLev : registeredFenceGates.stream().map(v -> v.scanLevel).distinct().toList()) {
            Set<Position> doorsAtLevel = registeredFenceGates.stream()
                    .filter(v -> v.scanLevel == scanLev)
                    .map(p -> new Position(p.x, p.z))
                    .collect(Collectors.toSet());
            int y1 = blockPos.offset(0, scanLev, 0).getY();
            updateActiveFarms(level, scanLev, y1, doorsAtLevel);
        }
        profileTick(start);
    }

    private void profileTick(long start) {
        if (Config.TICK_SAMPLING_RATE.get() == 0) {
            return;
        }
        long end = System.currentTimeMillis();
        times.add((int) (end - start));

        if (times.size() > Config.TICK_SAMPLING_RATE.get()) {
            Questown.LOGGER.debug(
                    "[TownRoomsMap] Average tick length: {}",
                    times.stream().mapToInt(Integer::intValue).average()
            );
            times.clear();
        }
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
    }

    public Collection<MCRoom> getAllRooms() {
        return this.activeRooms.values().stream().map(TownRooms::getAll).flatMap(Collection::stream).toList();
    }

    @Override
    public void updateRecipeForRoom(
            int scanLevel,
            MCRoom oldRoom,
            MCRoom newRoom,
            @Nullable RoomRecipeMatch resourceLocation
    ) {
        getOrCreateRooms(scanLevel);
        activeRecipes.get(scanLevel).update(oldRoom, newRoom, resourceLocation);
    }

    public int numRecipes() {
        return this.activeRecipes.size();
    }

    public Collection<RoomRecipeMatch<MCRoom>> getAllMatches() {
        Stream<RoomRecipeMatch<MCRoom>> objectStream = this.activeRecipes.values()
                .stream()
                .map(ActiveRecipes::entrySet)
                .flatMap(v -> v.stream().map(Map.Entry::getValue));
        return objectStream.collect(Collectors.toSet());
    }

    public void registerDoor(
            Position p,
            int scanLevel
    ) {
        registeredDoors.add(new TownPosition(p.x, p.z, scanLevel));
        Questown.LOGGER.debug("Door was registered at x={}, z={}, scanLevel={}", p.x, p.z, scanLevel);
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
            Optional<MCRoom> room = rooms.getAll().stream().filter(v -> v.getDoorPos().equals(pz)).findFirst();
            if (room.isEmpty()) {
                continue;
            }
            ActiveRecipes<MCRoom, RoomRecipeMatch<MCRoom>> recipes = activeRecipes.get(p.scanLevel);
            for (Map.Entry<MCRoom, RoomRecipeMatch<MCRoom>> m : recipes.entrySet()) {
                if (m.getKey().equals(room.get()) && m.getValue().getRecipeID().equals(recipeId)) {
                    b.add(m.getValue());
                }
            }
        }
        return b.build();
    }

    public Collection<MCRoom> getFarms() {
        return activeFarms.entrySet()
                .stream()
                .flatMap(v -> v.getValue().getAll().stream())
                .toList();
    }

}
