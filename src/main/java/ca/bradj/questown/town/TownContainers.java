package ca.bradj.questown.town;

import ca.bradj.questown.mobs.visitor.ContainerTarget;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

public class TownContainers {
    public static @Nullable ContainerTarget findMatching(
            TownFlagBlockEntity townFlagBlockEntity,
            ContainerTarget.CheckFn c
    ) {
        ServerLevel level = townFlagBlockEntity.getServerLevel();
        if (level == null) {
            return null;
        }
        Optional<ContainerTarget> found = findAllMatching(
                townFlagBlockEntity, c
        ).findFirst();
        return found.orElse(null);
    }

    public static Stream<ContainerTarget> findAllMatching(
            TownFlagBlockEntity townFlagBlockEntity,
            ContainerTarget.CheckFn c
    ) {
        ServerLevel level = townFlagBlockEntity.getServerLevel();
        if (level == null) {
            return Stream.empty();
        }
        Stream<ContainerTarget> allContainers = townFlagBlockEntity
                .getMatches()
                .stream()
                .flatMap(v -> v.getContainedBlocks().entrySet().stream())
                .filter(v -> v.getValue() instanceof ChestBlock)
                .map(v -> fromChestBlock(v.getKey(), (ChestBlock) v.getValue(), level));
        return allContainers.filter(v -> v.hasItem(c));
    }

    @NotNull
    public static ContainerTarget fromChestBlock(
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

        return new ContainerTarget(p, ChestBlock.getContainer(
                block,
                blockState,
                level,
                p,
                true
        ), () -> level.getBlockState(p) == blockState);
    }
}
