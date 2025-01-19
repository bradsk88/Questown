package ca.bradj.questown.town;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.UtilClean;
import ca.bradj.questown.integration.minecraft.MCContainer;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.items.StockRequestItem;
import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.town.interfaces.RoomsHolder;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.roomrecipes.adapter.Positions;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.roomrecipes.core.space.Position;
import ca.bradj.roomrecipes.serialization.MCRoom;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
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

    private static @NotNull Stream<UtilClean.Pair<MCRoom, Map.Entry<BlockPos, Block>>> getBlocks(
            RoomsHolder townFlagBlockEntity,
            Predicate<RoomRecipeMatch<MCRoom>> includeRoom,
            Predicate<BlockPos> includeBlock
    ) {
        return townFlagBlockEntity
                .getMatches(includeRoom)
                .stream()
                .flatMap(v -> getContainedBlocks(includeBlock, v));
    }

    private static @NotNull Stream<UtilClean.Pair<MCRoom, Map.Entry<BlockPos, Block>>> getContainedBlocks(
            Predicate<BlockPos> includeBlock,
            RoomRecipeMatch<MCRoom> v
    ) {
        return v.getContainedBlocks()
                .entrySet()
                .stream()
                .filter(z -> includeBlock.test(z.getKey()))
                .map(z -> new UtilClean.Pair<>(v.room, z));
    }

    @NotNull
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
        if (maybe == null) {
            throw new IllegalStateException("Null ContainerTarget is not allowed in this context");
        }
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
        Optional<Direction> facing = blockState.getOptionalValue(BlockStateProperties.HORIZONTAL_FACING);
        if (facing.isPresent()) {
            interactPos = Positions.FromBlockPos(p.relative(facing.get()));
        }

        if (blockState.isAir()) {
            return new ContainerTarget<>(
                    Positions.FromBlockPos(p), p.getY(), interactPos,
                    new MCContainer(ContainerTarget.REMOVED), () -> false,
                    item -> TownContainers.setWorkSpot(room, p, item),
                    REJECTS_EVERYTHING,
                    NO_BOOST
            );
        }

        if (!blockState.getBlock().equals(block)) {
            QT.BLOCK_LOGGER.error(
                    "Given block is not present at given position. Actual blockstate {}", blockState
            );
            return new ContainerTarget<>(
                    Positions.FromBlockPos(p), p.getY(), interactPos,
                    new MCContainer(ContainerTarget.REMOVED), () -> false,
                    item -> TownContainers.setWorkSpot(room, p, item),
                    REJECTS_EVERYTHING,
                    NO_BOOST
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
        MCContainer mcContainer = new MCContainer(container);

        return new ContainerTarget<>(
                position,
                p.getY(),
                interactPos,
                mcContainer,
                () -> level.getBlockState(p) == blockState,
                item -> TownContainers.setWorkSpot(room, p, item),
                ACCEPTS_ALL_ITEMS,
                NO_BOOST
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
                ((ContainerTarget.Container) blockEntity).getItemAcceptanceRankBoost().value()
        );
    }

    public static double comparison(
            BlockPos pos,
            ContainerTarget<MCContainer, MCTownItem> a
    ) {
        return pos.distSqr(a.getBlockPos()) / a.getRankingBoost();
    }
}
