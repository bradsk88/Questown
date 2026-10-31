package ca.bradj.questown.town.entity;

import ca.bradj.questown.blocks.MineshaftBlock;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.town.AbstractWorkStatusStore;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.Collection;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Pickaxe hits from a mineshaft while a villager is mining there (dev playtest 2026-10-30: "it
 * should emit pickaxe hit sounds every now and then while a villager is mining"). A miner away on
 * its trip has no work spot, but its work-state record at the mineshaft runs the trip's timer, so
 * a mineshaft position with a timer is being mined.
 */
final class MiningSounds {

    /** One chance in this many per flag tick, per mined shaft. */
    static final int CHANCE = 4;

    private MiningSounds() {
    }

    static void tick(ServerLevel level, Collection<? extends AbstractWorkStatusStore<BlockPos, ?, ?, ?>> stores) {
        for (BlockPos shaft : minedShafts(stores, p -> level.getBlockState(p).getBlock() instanceof MineshaftBlock)) {
            if (level.random.nextInt(CHANCE) == 0) {
                Compat.playSound(level, shaft, SoundEvents.STONE_HIT, SoundSource.BLOCKS);
            }
        }
    }

    static <POS> Collection<POS> minedShafts(
            Collection<? extends AbstractWorkStatusStore<POS, ?, ?, ?>> stores,
            Predicate<POS> isMineshaft
    ) {
        return stores.stream()
                .flatMap(s -> s.getAll().keySet().stream().filter(p -> isTiming(s, p)))
                .filter(isMineshaft)
                .collect(Collectors.toSet());
    }

    private static <POS> boolean isTiming(AbstractWorkStatusStore<POS, ?, ?, ?> store, POS p) {
        Integer left = store.getTimeToNextState(p);
        return left != null && left > 0;
    }
}
