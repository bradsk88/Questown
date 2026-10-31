package ca.bradj.questown.integration.minecraft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * When a chest a villager touched should open and close its lid. A touch opens a closed chest and
 * (re)starts its close timer, so a villager moving several items keeps the lid up instead of
 * flapping it per item. Pure, so the timing is unit-testable; {@link ChestTouches} does the world
 * side.
 */
public final class ChestLidTimers<P> {

    static final long OPEN_TICKS = 10;

    private final Map<P, Long> closeAt = new HashMap<>();

    /** Records a touch; true when the lid should open now (it was closed). */
    public boolean touch(P pos, long now) {
        boolean wasOpen = closeAt.containsKey(pos);
        closeAt.put(pos, now + OPEN_TICKS);
        return !wasOpen;
    }

    /** The chests whose lids should close now; they are forgotten. */
    public List<P> due(long now) {
        List<P> out = new ArrayList<>();
        closeAt.entrySet().removeIf(e -> {
            if (e.getValue() > now) {
                return false;
            }
            out.add(e.getKey());
            return true;
        });
        return out;
    }
}
