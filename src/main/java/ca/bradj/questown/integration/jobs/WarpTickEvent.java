package ca.bradj.questown.integration.jobs;

import ca.bradj.questown.world.QTWorldAccess;
import net.minecraft.core.BlockPos;

import java.util.Collection;
import java.util.function.Supplier;

/**
 * Event record for warp-interleaved hooks. Passed to
 * {@link JobPhaseModifier#onWarpTick} between villager
 * warp steps during time warp.
 * <p>{@code currentTick} is absolute game time: use it only in differences. {@code tickDelta} is the
 * ticks since the last call. Don't mix the two in one expression.
 *
 * @param currentTick        the current simulated game tick
 * @param tickDelta          ticks since the last call, nights included; scale effects by it
 * @param workBlockPositions the town's job block positions
 */
public record WarpTickEvent(
        QTWorldAccess world,
        long currentTick,
        long tickDelta,
        Supplier<Collection<BlockPos>> workBlockPositions
) {
}
