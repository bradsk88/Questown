package ca.bradj.questown.blocks;

import ca.bradj.questown.town.workstatus.State;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

public class JobBlock {

    public static @Nullable Integer getState(
            Function<BlockPos, State> sl,
            BlockPos bp
    ) {
        State oldState = sl.apply(bp);
        if (oldState == null) {
            return null;
        }
        return oldState.processingState();
    }

}
