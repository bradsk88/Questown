package ca.bradj.questown.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** A bed is two blocks and room recipes count both, so recipes list two bed entries per bed. */
public final class BedHalves {

    private BedHalves() {
    }

    /** The entries with every second bed dropped; an odd one is kept. */
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
