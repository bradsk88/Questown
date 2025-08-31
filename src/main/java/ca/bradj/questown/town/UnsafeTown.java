package ca.bradj.questown.town;

import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;

public class UnsafeTown extends Unsafe<TownFlagBlockEntity> {

    public UnsafeTown(Class<?> owner) {
        super(owner);
    }

    // Only safe to call from the server side
    @SuppressWarnings("DataFlowIssue")
    public @NotNull ServerLevel getServerLevelUnsafe() {
        return getUnsafe().getServerLevel();
    }
}
