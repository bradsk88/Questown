package ca.bradj.questown.jobs.special;

import ca.bradj.questown.integration.jobs.JobPhaseModifier;
import ca.bradj.questown.integration.jobs.WarpTickEvent;
import ca.bradj.questown.world.QTWorldAccess;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.ThreadLocalRandom;
import ca.bradj.questown.integration.jobs.QTNativeRule;

/**
 * Warp-interleaved hook that simulates crop growth during
 * time warp. Uses vanilla random tick probability:
 * 3/4096 chance per tick per block.
 *
 * Declared as a global rule in farmer job JSON files.
 * Effects are proportional to {@code event.tickDelta()}.
 *
 * For large tick deltas (>= 1365), deterministic integer
 * growths are applied per block. For small deltas, a
 * population-based fallback ensures visible progress:
 * the expected total growths across all eligible blocks
 * are computed and distributed randomly, avoiding the
 * problem where many independent low-probability rolls
 * all miss.
 * The "lore" for this is: Farmers tend the crops while the
 * player is away from town, so they grow (slightly) better.
 */
public class GrowCropsWarpRule extends JobPhaseModifier implements QTNativeRule {

    private static final int RANDOM_TICK_NUMERATOR = 3;
    private static final int RANDOM_TICK_DENOMINATOR = 4096;

    @Override
    public <X> X onWarpTick(X town, WarpTickEvent event) {
        QTWorldAccess world = event.world();
        long tickDelta = event.tickDelta();
        Collection<BlockPos> positions = event.workBlockPositions().get();

        int deterministicGrowths = deterministicGrowthsPerBlock(tickDelta);
        List<BlockPos> eligible = applyDeterministicGrowths(
                world, positions, deterministicGrowths
        );

        if (deterministicGrowths == 0 && !eligible.isEmpty()) {
            applyPopulationBasedGrowths(world, eligible, tickDelta);
        }

        return town;
    }

    private static int deterministicGrowthsPerBlock(long tickDelta) {
        return (int) (tickDelta * RANDOM_TICK_NUMERATOR / RANDOM_TICK_DENOMINATOR);
    }

    private static List<BlockPos> applyDeterministicGrowths(
            QTWorldAccess world,
            Collection<BlockPos> positions,
            int growths
    ) {
        List<BlockPos> eligible = new ArrayList<>();
        for (BlockPos pos : positions) {
            if (!isGrowableCrop(world, pos)) {
                continue;
            }
            if (growths > 0) {
                growBy(world, pos, growths);
            } else {
                eligible.add(pos);
            }
        }
        return eligible;
    }

    private static void applyPopulationBasedGrowths(
            QTWorldAccess world,
            List<BlockPos> eligible,
            long tickDelta
    ) {
        double perBlockChance = tickDelta * RANDOM_TICK_NUMERATOR / (double) RANDOM_TICK_DENOMINATOR;
        double totalExpected = perBlockChance * eligible.size();
        int guaranteed = (int) totalExpected;
        double remainder = totalExpected - guaranteed;

        eligible = world.getShuffledCopy(eligible);

        for (int i = 0; i < guaranteed && i < eligible.size(); i++) {
            growByOne(world, eligible.get(i));
        }
        if (remainder > 0 && guaranteed < eligible.size()
                && ThreadLocalRandom.current().nextDouble() < remainder) {
            growByOne(world, eligible.get(guaranteed));
        }
    }

    private static boolean isGrowableCrop(QTWorldAccess world, BlockPos pos) {
        OptionalInt age = world.getBlockIntProperty(pos, "age");
        if (age.isEmpty()) {
            return false;
        }
        OptionalInt maxAge = world.getMaxBlockIntProperty(pos, "age");
        if (maxAge.isEmpty()) {
            return false;
        }
        return age.getAsInt() < maxAge.getAsInt();
    }

    private static void growBy(QTWorldAccess world, BlockPos pos, int amount) {
        OptionalInt age = world.getBlockIntProperty(pos, "age");
        OptionalInt maxAge = world.getMaxBlockIntProperty(pos, "age");
        if (age.isEmpty() || maxAge.isEmpty()) {
            return;
        }
        int newAge = Math.min(age.getAsInt() + amount, maxAge.getAsInt());
        if (newAge != age.getAsInt()) {
            world.setBlockIntProperty(pos, "age", newAge);
        }
    }

    private static void growByOne(QTWorldAccess world, BlockPos pos) {
        growBy(world, pos, 1);
    }
}
