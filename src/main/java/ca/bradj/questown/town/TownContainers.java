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
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class TownContainers {
    public static @Nullable ContainerTarget<MCContainer, MCTownItem> findMatching(
            TownInterface townFlagBlockEntity,
            ContainerTarget.CheckFn<MCTownItem> c
    ) {
        ServerLevel level = townFlagBlockEntity.getServerLevel();
        if (level == null) {
            return null;
        }
        Optional<ContainerTarget<MCContainer, MCTownItem>> found = findAllMatching(
                townFlagBlockEntity, c, x -> true
        ).findFirst();
        return found.orElse(null);
    }

    public static Stream<ContainerTarget<MCContainer, MCTownItem>> findAllMatching(
            TownInterface t,
            ContainerTarget.CheckFn<MCTownItem> c,
            Predicate<RoomRecipeMatch<MCRoom>> include
    ) {
        ServerLevel level = t.getServerLevel();
        if (level == null) {
            return Stream.empty();
        }
        List<ContainerTarget<MCContainer, MCTownItem>> allContainers = getAllContainersStream(t.getRoomHandle(), level, include).toList();
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
        return getAllContainersStream(townFlagBlockEntity.getRoomHandle(), level, include).toList();
    }

    @NotNull
    private static Stream<ContainerTarget<MCContainer, MCTownItem>> getAllContainersStream(
            RoomsHolder townFlagBlockEntity,
            ServerLevel level,
            Predicate<RoomRecipeMatch<MCRoom>> include
    ) {
        return townFlagBlockEntity
                .getMatches(include)
                .stream()
                .flatMap(v -> v.getContainedBlocks().entrySet().stream().map(z -> new UtilClean.Pair<>(v.room, z)))
                .filter(v -> v.b().getValue() instanceof ChestBlock)
                .map(v -> fromChestBlockMaybe(v.a(), v.b().getKey(), (ChestBlock) v.b().getValue(), level))
                .filter(Objects::nonNull);
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
                    item -> TownContainers.setWorkSpot(room, p, item)
            );
        }

        if (!blockState.getBlock().equals(block)) {
            QT.BLOCK_LOGGER.error(
                    "Given block is not present at given position. Actual blockstate {}", blockState
            );
            return new ContainerTarget<>(
                    Positions.FromBlockPos(p), p.getY(), interactPos,
                    new MCContainer(ContainerTarget.REMOVED), () -> false,
                    item -> TownContainers.setWorkSpot(room, p, item)
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
                item -> TownContainers.setWorkSpot(room, p, item)
        );
    }

    private static void setWorkSpot(MCRoom room, BlockPos p, MCTownItem item) {
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
            BlockPos pos
    ) {
        return findClosestMatching(town, checkFn, pos, x -> true);
    }
    public static @Nullable ContainerTarget<MCContainer, MCTownItem> findClosestMatching(
            TownInterface town,
            ContainerTarget.CheckFn<MCTownItem> checkFn,
            BlockPos pos,
            Predicate<RoomRecipeMatch<MCRoom>> include
    ) {
        Stream<ContainerTarget<MCContainer, MCTownItem>> all = findAllMatching(town, checkFn, include);
        return all.min(Comparator.comparingDouble(a -> pos.distSqr(a.getBlockPos()))).orElse(null);
    }
}
