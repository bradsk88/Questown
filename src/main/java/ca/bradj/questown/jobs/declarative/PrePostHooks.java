package ca.bradj.questown.jobs.declarative;

import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiFunction;

public class PrePostHooks {

    static <X, TOWN> TOWN processMulti(
            TOWN initialTown,
            ImmutableList<X> appliers,
            BiFunction<TOWN, X, TOWN> fn
    ) {

        TOWN out = initialTown;
        boolean nothingDone = true;
        for (X m : appliers) {
            @Nullable TOWN o = fn.apply(out, m);
            if (o == null) {
                continue;
            }
            nothingDone = false;
            out = o;
        }
        if (nothingDone) {
            return null;
        }
        return out;
    }
}
