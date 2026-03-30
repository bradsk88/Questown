package ca.bradj.questown.integration.jobs;

import ca.bradj.questown.world.QTWorldAccess;
import net.minecraft.core.BlockPos;

import java.util.Collection;
import java.util.function.Supplier;

/**
 * Event record for warp-interleaved hooks. Passed to
 * {@link JobPhaseModifier#onWarpTick} between villager
 * warp steps during time warp.
 *
 * @param world              World access (QTWorldAccess,
 *                           not ServerLevel) for testability
 * @param currentTick        The current simulated game tick
 * @param tickDelta          Ticks elapsed since last invocation.
 *                           Hooks should compute effects
 *                           proportionally to this value,
 *                           not assume any particular call
 *                           frequency.
 * @param workBlockPositions Tracked job block positions from
 *                           the town's work state registry
 */
public record WarpTickEvent(
        QTWorldAccess world,
        long currentTick,
        long tickDelta,
        Supplier<Collection<BlockPos>> workBlockPositions
) {
}
