package ca.bradj.questown.town;

import ca.bradj.questown.mobs.visitor.ContainerTarget;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public class TownContainers {
    public static @Nullable ContainerTarget findMatching(
            TownFlagBlockEntity townFlagBlockEntity,
            ContainerTarget.CheckFn c) {
        ServerLevel level = townFlagBlockEntity.getServerLevel();
        if (level == null) {
            return null;
        }
        Optional<ContainerTarget> found = townFlagBlockEntity
                .getMatches()
                .stream()
                .flatMap(v -> v.getContainedBlocks().entrySet().stream())
                .filter(v -> v.getValue() instanceof ChestBlock)
                .map(v -> {
                    BlockState blockState = level.getBlockState(v.getKey());
                    return new ContainerTarget(v.getKey(), ChestBlock.getContainer(
                            (ChestBlock) v.getValue(),
                            blockState,
                            level,
                            v.getKey(),
                            true
                    ), () -> level.getBlockState(v.getKey()) == blockState);
                })
                .filter(v -> v.hasItem(c))
                .findFirst();
        return found.orElse(null);
    }
}
