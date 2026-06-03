package ca.bradj.questown.integration.jobs;

import ca.bradj.questown.world.QTWorldAccess;
import net.minecraft.core.BlockPos;

import java.util.Collection;
import java.util.function.Supplier;

/**
 * Event record for warp-interleaved hooks. Passed to
 * {@link JobPhaseModifier#onWarpTick} between villager
 * warp steps during time warp.
 * <p>
 * <b>Footgun (ADR-0006):</b> {@code currentTick} and {@code tickDelta} have
 * <b>different zero-points</b> by design. {@code currentTick} is <b>absolute</b>
 * (dayTime-anchored) — use it only in <i>differences</i> (e.g.
 * {@code currentTick - plantTick}), which is what makes the seeded-sapling
 * {@code plantTick = 0} "grow early" sentinel work. {@code tickDelta} is the
 * <b>window-relative</b> wall-clock increment that sums to the warp's
 * {@code wallClockTicks}. Never combine the two in one expression (don't sum
 * {@code currentTick}s, and don't add a stored {@code tickDelta} to a
 * {@code currentTick}).
 *
 * @param world              World access (QTWorldAccess,
 *                           not ServerLevel) for testability
 * @param currentTick        The current simulated game tick (absolute,
 *                           dayTime-anchored; compare only as a difference)
 * @param tickDelta          Wall-clock ticks elapsed since last invocation
 *                           (night-inclusive in warp). Hooks should compute
 *                           effects proportionally to this value, not assume
 *                           any particular call frequency.
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
