package ca.bradj.questown.blocks;

import ca.bradj.questown.town.AbstractWorkStatusStore;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

public class JobBlock {

    public static <P> @Nullable Integer getState(
            Function<P, AbstractWorkStatusStore.State> sl,
            P bp
    ) {
        AbstractWorkStatusStore.State oldState = sl.apply(bp);
        if (oldState == null) {
            return null;
        }
        return oldState.processingState();
    }

}
