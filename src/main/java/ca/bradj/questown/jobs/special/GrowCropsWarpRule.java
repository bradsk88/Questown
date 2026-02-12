package ca.bradj.questown.jobs.special;

import ca.bradj.questown.integration.jobs.JobPhaseModifier;
import ca.bradj.questown.integration.jobs.WarpTickEvent;
import ca.bradj.questown.world.QTWorldAccess;
import net.minecraft.core.BlockPos;

import java.util.Collection;
import java.util.OptionalInt;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Warp-interleaved hook that simulates crop growth during
 * time warp. Uses vanilla random tick probability:
 * 3/4096 chance per tick per block.
 *
 * Declared as a global rule in farmer job JSON files.
 * Effects are proportional to {@code event.tickDelta()}.
 *
 * FIXME: Doesn't seem to do anything if warp is only 1000 ticks
 */
public class GrowCropsWarpRule extends JobPhaseModifier {

    @Override
    public <X> X onWarpTick(X town, WarpTickEvent event) {
        QTWorldAccess world = event.world();
        long tickDelta = event.tickDelta();
        Collection<BlockPos> positions = event.workBlockPositions().get();
        for (BlockPos pos : positions) {
            OptionalInt age =
                    world.getBlockIntProperty(pos, "age");
            if (age.isEmpty()) {
                continue;
            }
            OptionalInt maxAge =
                    world.getMaxBlockIntProperty(pos, "age");
            if (maxAge.isEmpty()) {
                continue;
            }
            if (age.getAsInt() >= maxAge.getAsInt()) {
                continue;
            }

            // Vanilla random tick: 3/4096 chance per tick
            int growths = (int) (tickDelta * 3 / 4096);
            if (growths == 0
                    && ThreadLocalRandom.current()
                            .nextInt(4096) < tickDelta * 3) {
                growths = 1;
            }

            int newAge = Math.min(
                    age.getAsInt() + growths,
                    maxAge.getAsInt()
            );
            if (newAge != age.getAsInt()) {
                world.setBlockIntProperty(
                        pos, "age", newAge
                );
            }
        }
        return town;
    }
}
