package ca.bradj.questown.town;

import ca.bradj.questown.mobs.visitor.FoodTarget;
import ca.bradj.questown.town.interfaces.TownInterface;
import com.google.common.collect.ImmutableSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.ChestBlock;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public class TownContainers {
    public static @Nullable FoodTarget findMatching(
            TownFlagBlockEntity townFlagBlockEntity,
            TownInterface.Checker c) {
        ServerLevel level = townFlagBlockEntity.getServerLevel();
        if (level == null) {
            return null;
        }
        Optional<FoodTarget> found = townFlagBlockEntity
                .getMatches()
                .stream()
                .flatMap(v -> v.getContainedBlocks().entrySet().stream())
                .filter(v -> v.getValue() instanceof ChestBlock)
                .map(v -> new FoodTarget(v.getKey(), ChestBlock.getContainer(
                        (ChestBlock) v.getValue(),
                        level.getBlockState(v.getKey()),
                        level,
                        v.getKey(),
                        true
                )))
                .filter(v -> v.hasAnyOf(
                        ImmutableSet.of(Items.BREAD) // TODO: Get from tag?
                ))
                .findFirst();
        return found.orElse(null);
    }
}
