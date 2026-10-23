package ca.bradj.questown.town;

import net.minecraft.core.BlockPos;

import java.util.Collection;
import java.util.List;

/**
 * When a town sleeps: no player is within the tick radius of any of its townies (of its flag,
 * while it has no townies yet). A sleeping town runs nothing, its townies included; waking after a
 * real absence, warp catches it up. Measured from the townies rather than the flag so a player near
 * any townie never watches it stand frozen. See {@code CONTEXT.md} "Town sleep".
 */
public final class TownSleepRadius {

    private TownSleepRadius() {
    }

    /**
     * The squared distance between the nearest player and the town: its nearest townie, or the
     * flag while the town has no townies. {@link Long#MAX_VALUE} when there are no players.
     */
    public static long nearestPlayerDistSqr(
            Collection<BlockPos> players,
            Collection<BlockPos> townies,
            BlockPos flag
    ) {
        Collection<BlockPos> anchors = townies.isEmpty() ? List.of(flag) : townies;
        long nearest = Long.MAX_VALUE;
        for (BlockPos player : players) {
            for (BlockPos anchor : anchors) {
                nearest = Math.min(nearest, (long) player.distSqr(anchor));
            }
        }
        return nearest;
    }

    /** Asleep when the nearest player is farther than the (squared) tick radius. */
    public static boolean isAsleep(
            long nearestPlayerDistSqr,
            long tickRadiusSqr
    ) {
        return nearestPlayerDistSqr > tickRadiusSqr;
    }
}
