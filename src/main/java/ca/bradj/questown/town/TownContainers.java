package ca.bradj.questown.town;

import ca.bradj.questown.mobs.visitor.ContainerTarget;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.ChestBlock;
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
                .map(v -> new ContainerTarget(v.getKey(), ChestBlock.getContainer(
                        (ChestBlock) v.getValue(),
                        level.getBlockState(v.getKey()),
                        level,
                        v.getKey(),
                        true
                )))
                .filter(v -> v.hasItem(c))
                .findFirst();
        return found.orElse(null);
    }
}
