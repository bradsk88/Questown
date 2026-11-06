package ca.bradj.questown.town;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.Pair;
import ca.bradj.questown.integration.minecraft.ChestTouches;
import ca.bradj.questown.integration.minecraft.MCContainer;
import ca.bradj.questown.integration.minecraft.DisplaySlots;
import ca.bradj.questown.integration.minecraft.MCDisplayContainer;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.items.StockRequestItem;
import ca.bradj.questown.jobs.InteractionSpots;
import ca.bradj.questown.jobs.WalkTargets;
import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import ca.bradj.questown.town.interfaces.RoomsHolder;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.roomrecipes.adapter.Positions;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.roomrecipes.core.space.Position;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class TownContainers {

    public static final Predicate<MCTownItem> ACCEPTS_ALL_ITEMS = i -> true;
    public static final Predicate<MCTownItem> REJECTS_EVERYTHING = i -> false;
    public static final float NO_BOOST = 1.0f;

    public static @Nullable ContainerTarget<MCContainer, MCTownItem> findMatching(
            TownInterface townFlagBlockEntity,
            ContainerTarget.CheckFn<MCTownItem> c
    ) {
        ServerLevel level = townFlagBlockEntity.getServerLevel();
        if (level == null) {
            return null;
        }
        Optional<ContainerTarget<MCContainer, MCTownItem>> found = findAllContainersMatching(
                townFlagBlockEntity, c
        ).findFirst();
        return found.orElse(null);
    }

    public static Stream<ContainerTarget<MCContainer, MCTownItem>> findAllContainersMatching(
            TownInterface t,
            ContainerTarget.CheckFn<MCTownItem> c
    ) {
        ServerLevel level = t.getServerLevel();
        if (level == null) {
            return Stream.empty();
        }
        List<ContainerTarget<MCContainer, MCTownItem>> allContainers = getAllContainers(
                t,
                t.getServerLevel()
        );
        return allContainers.stream().filter(v -> v.hasItem(c));
    }

    public static List<ContainerTarget<MCContainer, MCTownItem>> getAllContainers(
            TownInterface townFlagBlockEntity,
            ServerLevel level
    ) {
        return getAllContainers(townFlagBlockEntity, level, (x) -> true);
    }

    public static List<ContainerTarget<MCContainer, MCTownItem>> getAllContainers(
            TownInterface townFlagBlockEntity,
            ServerLevel level,
            Predicate<RoomRecipeMatch<MCRoom>> include
    ) {
        Stream<ContainerTarget<MCContainer, MCTownItem>> allChestsStream = getAllChestsStream(
                townFlagBlockEntity.getRoomHandle(),
                level,
                include,
                x -> true
        );
        Stream<ContainerTarget<MCContainer, MCTownItem>> entities = getAllContainerEntitiesStream(
                townFlagBlockEntity.getRoomHandle(),
                level,
                include,
                x -> true
        );
        return Stream.concat(allChestsStream, entities).toList();
    }

    /** Every container plus the displays in town rooms, for counting what the town holds. */
    public static List<ContainerTarget<MCContainer, MCTownItem>> getAllContainersAndDisplays(
            TownInterface town,
            ServerLevel level
    ) {
        return Stream.concat(
                getAllContainers(town, level).stream(),
                getAllDisplaysStream(town.getRoomHandle(), level, x -> true)
        ).toList();
    }

    public static boolean isDisplay(ContainerTarget<?, ?> target) {
        return target.getContainer() instanceof MCDisplayContainer;
    }

    /** Armor stands and item frames in town rooms. */
    private static Stream<ContainerTarget<MCContainer, MCTownItem>> getAllDisplaysStream(
            RoomsHolder rooms,
            ServerLevel level,
            Predicate<RoomRecipeMatch<MCRoom>> includeRoom
    ) {
        return rooms.getMatches(includeRoom).stream().flatMap(m -> displaysIn(m, level));
    }

    private static Stream<ContainerTarget<MCContainer, MCTownItem>> displaysIn(
            RoomRecipeMatch<MCRoom> match,
            ServerLevel level
    ) {
        Set<BlockPos> positions = match.getContainedBlocks().keySet();
        return BoundingBox.encapsulatingPositions(positions).map(AABB::of).stream()
                    .flatMap(box -> level.getEntitiesOfClass(Entity.class, box, DisplaySlots::isDisplay).stream())
                    .filter(e -> positions.contains(e.blockPosition()))
                    .map(e -> fromDisplay(match.room, e));
    }

    private static ContainerTarget<MCContainer, MCTownItem> fromDisplay(
            MCRoom room,
            Entity e
    ) {
        BlockPos p = e.blockPosition();
        Position position = Positions.FromBlockPos(p);
        return new ContainerTarget<>(
                position,
                p.getY(),
                position,
                new MCDisplayContainer(DisplaySlots.of(e)),
                () -> e.isAlive() && e.blockPosition().equals(p),
                item -> {
                },
                REJECTS_EVERYTHING,
                NO_BOOST,
                room
        );
    }

    @NotNull
    private static Stream<ContainerTarget<MCContainer, MCTownItem>> getAllChestsStream(
            RoomsHolder townFlagBlockEntity,
            ServerLevel level,
            Predicate<RoomRecipeMatch<MCRoom>> includeRoom,
            Predicate<BlockPos> includeBlock
    ) {
        return getBlocks(townFlagBlockEntity, includeRoom, includeBlock)
                .filter(v -> v.b().getValue() instanceof ChestBlock)
                .map(v -> fromChestBlockMaybe(v.a(), v.b().getKey(), (ChestBlock) v.b().getValue(), level))
                .filter(Objects::nonNull);
    }

    @SuppressWarnings("rawtypes")
    @NotNull
    private static Stream<ContainerTarget<MCContainer, MCTownItem>> getAllContainerEntitiesStream(
            RoomsHolder townFlagBlockEntity,
            ServerLevel level,
            Predicate<RoomRecipeMatch<MCRoom>> includeRoom,
            Predicate<BlockPos> includeBlock
    ) {
        return getBlocks(townFlagBlockEntity, includeRoom, includeBlock)
                .map(v -> fromEntity(level, v.b().getKey())).
                filter(Objects::nonNull);
    }

    private static @NotNull Stream<Pair<MCRoom, Map.Entry<BlockPos, Block>>> getBlocks(
            RoomsHolder townFlagBlockEntity,
            Predicate<RoomRecipeMatch<MCRoom>> includeRoom,
            Predicate<BlockPos> includeBlock
    ) {
        return townFlagBlockEntity
                .getMatches(includeRoom)
                .stream()
                .flatMap(v -> getContainedBlocks(includeBlock, v));
    }

    private static @NotNull Stream<Pair<MCRoom, Map.Entry<BlockPos, Block>>> getContainedBlocks(
            Predicate<BlockPos> includeBlock,
            RoomRecipeMatch<MCRoom> v
    ) {
        return v.getContainedBlocks()
                .entrySet()
                .stream()
                .filter(z -> includeBlock.test(z.getKey()))
                .map(z -> new Pair<>(v.room, z));
    }

    @Nullable
    public static ContainerTarget<MCContainer, MCTownItem> fromChestBlock(
            MCRoom room,
            BlockPos p,
            ChestBlock block,
            ServerLevel level
    ) {
        ContainerTarget<MCContainer, MCTownItem> maybe = fromChestBlockMaybe(
                room,
                p,
                block,
                level
        );
        return maybe;
    }

    @Nullable
    private static ContainerTarget<MCContainer, MCTownItem> fromChestBlockMaybe(
            MCRoom room,
            BlockPos p,
            ChestBlock block,
            ServerLevel level
    ) {
        Position position = Positions.FromBlockPos(p);
        Position interactPos = position;

        BlockState blockState = level.getBlockState(p);
        if (room != null) {
            // Where a villager can stand, door side first, never inside a wall.
            BlockPos spot = InteractionSpots.find(p, room, b -> WalkTargets.isStandable(level, b));
            if (spot == null) {
                spot = p.relative(InteractionSpots.doorDirectionFromCenter(room));
            }
            interactPos = Positions.FromBlockPos(spot);
        } else {
            Optional<Direction> facing = blockState.getOptionalValue(BlockStateProperties.HORIZONTAL_FACING);
            if (facing.isPresent()) {
                interactPos = Positions.FromBlockPos(p.relative(facing.get()));
            }
        }

        if (blockState.isAir()) {
            return new ContainerTarget<>(
                    Positions.FromBlockPos(p), p.getY(), interactPos,
                    new MCContainer(ContainerTarget.REMOVED), () -> false,
                    item -> TownContainers.setWorkSpot(room, p, item),
                    REJECTS_EVERYTHING,
                    NO_BOOST,
                    room
            );
        }

        if (!blockState.getBlock().equals(block)) {
            // The block at a recorded chest changed; expected, so debug only.
            QT.BLOCK_LOGGER.debug(
                    "Given block is not present at given position. Actual blockstate {}", blockState
            );
            return new ContainerTarget<>(
                    Positions.FromBlockPos(p), p.getY(), interactPos,
                    new MCContainer(ContainerTarget.REMOVED), () -> false,
                    item -> TownContainers.setWorkSpot(room, p, item),
                    REJECTS_EVERYTHING,
                    NO_BOOST,
                    room
            );
        }

        ChestType typ = blockState.getOptionalValue(ChestBlock.TYPE).orElse(null);
        if (!ChestType.LEFT.equals(typ) && !ChestType.SINGLE.equals(typ)) {
            return null;
        }

        Container container = ChestBlock.getContainer(
                block,
                blockState,
                level,
                p,
                true
        );
        if (container == null) {
            throw new IllegalStateException("Container is null at " + p);
        }
        MCContainer mcContainer = new MCContainer(container, () -> ChestTouches.touch(level, p));

        return new ContainerTarget<>(
                position,
                p.getY(),
                interactPos,
                mcContainer,
                () -> level.getBlockState(p) == blockState,
                item -> TownContainers.setWorkSpot(room, p, item),
                ACCEPTS_ALL_ITEMS,
                NO_BOOST,
                room
        );
    }

    private static void setWorkSpot(
            MCRoom room,
            BlockPos p,
            MCTownItem item
    ) {
        if (item.get().getDefaultInstance().isEmpty()) {
            return;
        }
        if (item.get() instanceof StockRequestItem) {
            if (room == null) {
                // No room to link the request to.
                return;
            }
            if (StockRequestItem.hasRoom(item.getItemNBT())) {
                return;
            }
            item.setNBT(tag -> StockRequestItem.writeToNBT(tag, room, p));
        }
    }

    public static @Nullable ContainerTarget<MCContainer, MCTownItem> findClosestMatching(
            TownInterface town,
            ContainerTarget.CheckFn<MCTownItem> checkFn,
            BlockPos pos,
            Collection<MCTownItem> itemsThatMustBeInserted
    ) {
        Stream<ContainerTarget<MCContainer, MCTownItem>> chests = findAllContainersMatching(
                town,
                checkFn
        );
        if (!itemsThatMustBeInserted.isEmpty()) {
            chests = chests.filter(v -> itemsThatMustBeInserted.stream().anyMatch(v::canAccept));
        }

        return chests.min(Comparator.comparingDouble(a -> comparison(pos, a))).orElse(null);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static @Nullable ContainerTarget<MCContainer, MCTownItem> fromEntity(
            ServerLevel level,
            BlockPos p
    ) {
        BlockState blockState = level.getBlockState(p);
        Optional<Direction> facing = blockState.getOptionalValue(BlockStateProperties.HORIZONTAL_FACING);
        Position position = Positions.FromBlockPos(p);
        Position interactPos = position;
        if (facing.isPresent()) {
            interactPos = Positions.FromBlockPos(p.relative(facing.get()));
        }
        BlockEntity blockEntity = level.getBlockEntity(p);
        if (blockEntity == null) {
            return null;
        }
        if (!(blockEntity instanceof ContainerTarget.Container<?>)) {
            return null;
        }
        return new ContainerTarget<MCContainer, MCTownItem>(
                position,
                p.getY(),
                interactPos,
                (ContainerTarget.Container) blockEntity,
                () -> level.getBlockState(p) == blockState,
                item -> {
                }, // TODO: Allow players to put organizer requests in non-chest containers?
                item -> ((ContainerTarget.Container) blockEntity).canAcceptIfSpaceAllows(item),
                ((ContainerTarget.Container) blockEntity).getItemAcceptanceRankBoost().value(),
                null
        );
    }

    public static double comparison(
            BlockPos pos,
            ContainerTarget<MCContainer, MCTownItem> a
    ) {
        return pos.distSqr(a.getBlockPos()) / a.getRankingBoost();
    }

    public static Collection<Item> getUniqueItems(TownInterface t) {
        // TODO[Performance]: "All containers" should probably be stored on the
        //  TownFlagBlockEntity until the containers change.
        List<ContainerTarget<MCContainer, MCTownItem>> all = getAllContainers(t, t.getServerLevel());
        Set<Item> uniqueItems = new HashSet<>();
        for (ContainerTarget<MCContainer, MCTownItem> container : all) {
            if (container.getContainer() == null) {
                continue;
            }
            for (int i = 0; i < container.getContainer().size(); i++) {
                ItemStack stack = container.getContainer().getItem(i).toMCItemStack();
                if (!stack.isEmpty()) {
                    uniqueItems.add(stack.getItem());
                }
            }
        }
        return uniqueItems;
    }

    public static ImmutableList<MCTownItem> getAllStacks(
            TownFlagBlockEntity e,
            ServerLevel serverLevel
    ) {
        List<ContainerTarget<MCContainer, MCTownItem>> cs = getAllContainersAndDisplays(e, serverLevel);
        return cs.stream().map(ContainerTarget::getItems).flatMap(Collection::stream).filter(v -> !v.isEmpty())
                 .collect(ImmutableList.toImmutableList());
    }
}
