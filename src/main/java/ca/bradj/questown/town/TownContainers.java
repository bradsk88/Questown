package ca.bradj.questown.town;

import ca.bradj.questown.QT;
import ca.bradj.questown.integration.minecraft.MCContainer;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.roomrecipes.adapter.Positions;
import ca.bradj.roomrecipes.core.space.Position;
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

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public class TownContainers {
    public static @Nullable ContainerTarget<MCContainer, MCTownItem> findMatching(
            TownFlagBlockEntity townFlagBlockEntity,
            ContainerTarget.CheckFn<MCTownItem> c
    ) {
        ServerLevel level = townFlagBlockEntity.getServerLevel();
        if (level == null) {
            return null;
        }
        Optional<ContainerTarget<MCContainer, MCTownItem>> found = findAllMatching(
                townFlagBlockEntity, c
        ).findFirst();
        return found.orElse(null);
    }

    public static Stream<ContainerTarget<MCContainer, MCTownItem>> findAllMatching(
            TownFlagBlockEntity townFlagBlockEntity,
            ContainerTarget.CheckFn<MCTownItem> c
    ) {
        ServerLevel level = townFlagBlockEntity.getServerLevel();
        if (level == null) {
            return Stream.empty();
        }
        Stream<ContainerTarget<MCContainer, MCTownItem>> allContainers = getAllContainersStream(townFlagBlockEntity, level);
        return allContainers.filter(v -> v.hasItem(c));
    }

    public static List<ContainerTarget<MCContainer, MCTownItem>> getAllContainers(TownFlagBlockEntity townFlagBlockEntity, ServerLevel level) {
        return getAllContainersStream(townFlagBlockEntity, level).toList();
    }

        @NotNull
    private static Stream<ContainerTarget<MCContainer, MCTownItem>> getAllContainersStream(TownFlagBlockEntity townFlagBlockEntity, ServerLevel level) {
        return townFlagBlockEntity
                .getMatches()
                .stream()
                .flatMap(v -> v.getContainedBlocks().entrySet().stream())
                .filter(v -> v.getValue() instanceof ChestBlock)
                .map(v -> fromChestBlockMaybe(v.getKey(), (ChestBlock) v.getValue(), level))
                .filter(Objects::nonNull);
    }

    @NotNull
    public static ContainerTarget<MCContainer, MCTownItem> fromChestBlock(
            BlockPos p,
            ChestBlock block,
            ServerLevel level
    ) {
        ContainerTarget<MCContainer, MCTownItem> maybe = fromChestBlockMaybe(
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
                    new MCContainer(ContainerTarget.REMOVED), () -> false
            );
        }

        if (!blockState.getBlock().equals(block)) {
            QT.BLOCK_LOGGER.error(
                    "Given block is not present at given position. Actual blockstate {}", blockState
            );
            return new ContainerTarget<>(
                    Positions.FromBlockPos(p), p.getY(), interactPos,
                    new MCContainer(ContainerTarget.REMOVED), () -> false
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
                () -> level.getBlockState(p) == blockState
        );
    }
}
