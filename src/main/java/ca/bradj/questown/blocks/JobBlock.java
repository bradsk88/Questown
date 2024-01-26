package ca.bradj.questown.blocks;

import ca.bradj.questown.town.AbstractWorkStatusStore;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

public class JobBlock {

    public static @Nullable Integer getState(
            Function<BlockPos, AbstractWorkStatusStore.State> sl,
            BlockPos bp
    ) {
        AbstractWorkStatusStore.State oldState = sl.apply(bp);
        if (oldState == null) {
            return null;
        }
        return oldState.processingState();
    }

}
