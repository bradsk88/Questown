package ca.bradj.questown.town;

import ca.bradj.questown.integration.minecraft.MCContainer;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.mobs.visitor.ContainerTarget;
import ca.bradj.roomrecipes.adapter.Positions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
        Stream<ContainerTarget<MCContainer, MCTownItem>> allContainers = townFlagBlockEntity
                .getMatches()
                .stream()
                .flatMap(v -> v.getContainedBlocks().entrySet().stream())
                .filter(v -> v.getValue() instanceof ChestBlock)
                .map(v -> fromChestBlockMaybe(v.getKey(), (ChestBlock) v.getValue(), level))
                .filter(Objects::nonNull);
        return allContainers.filter(v -> v.hasItem(c));
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
        BlockState blockState = level.getBlockState(p);
        if (!blockState.getBlock().equals(block)) {
            throw new IllegalArgumentException(String.format(
                    "Given block is not present at given position. Actual blockstate %s", blockState
            ));
        }

        ChestType typ = blockState.getOptionalValue(ChestBlock.TYPE).orElse(null);
        if (!ChestType.LEFT.equals(typ) && !ChestType.SINGLE.equals(typ)) {
            return null;
        }

        MCContainer mcContainer = new MCContainer(ChestBlock.getContainer(
                block,
                blockState,
                level,
                p,
                true
        ));
        return new ContainerTarget<>(
                Positions.FromBlockPos(p),
                p.getY(),
                mcContainer,
                () -> level.getBlockState(p) == blockState
        );
    }
}
