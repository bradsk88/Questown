package ca.bradj.questown.integration.minecraft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** When a villager-touched chest opens and closes its lid. Repeated touches keep it open instead of flapping. */
public final class ChestLidTimers<P> {

    static final long OPEN_TICKS = 10;

    private final Map<P, Long> closeAt = new HashMap<>();

    public boolean touch(P pos, long now) {
        boolean wasOpen = closeAt.containsKey(pos);
        closeAt.put(pos, now + OPEN_TICKS);
        return !wasOpen;
    }

    /** The chests whose lids should close now. */
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
