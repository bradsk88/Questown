package ca.bradj.questown.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * A bed is two blocks and room recipes count both, so a recipe lists two bed entries per bed
 * (CONTEXT.md "Bed room recipe"). Anything that turns recipe entries into whole beds must take
 * one entry per bed.
 */
public final class BedHalves {

    private BedHalves() {
    }

    /**
     * The entries with every second bed dropped: Bedroom's light + 2 bed blocks becomes light + 1
     * bed. An odd bed entry is kept, so a bed is never lost. Non-bed entries keep their order.
     */
    public static <T> List<T> oneEntryPerBed(
            List<T> entries,
            Predicate<T> isBed
    ) {
        List<T> out = new ArrayList<>();
        boolean otherHalfPending = false;
        for (T e : entries) {
            if (!isBed.test(e)) {
                out.add(e);
                continue;
            }
            if (!otherHalfPending) {
                out.add(e);
            }
            otherHalfPending = !otherHalfPending;
        }
        return out;
    }
}
