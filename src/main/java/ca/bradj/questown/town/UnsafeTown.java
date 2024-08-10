package ca.bradj.questown.town;

import net.minecraft.Util;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class UnsafeTown {

    @Nullable
    TownFlagBlockEntity town = null;

    public void initialize(TownFlagBlockEntity t) {
        this.town = t;
    }

    // Only safe to call after initialized
    public @NotNull TownFlagBlockEntity getUnsafe() {
        if (town == null) {
            throw new IllegalStateException("Town has not been initialized on quest handle yet");
        }
        return town;
    }

    // Only safe to call from the server side
    public @NotNull ServerLevel getServerLevelUnsafe() {
        return getUnsafe().getServerLevel();
    }
}
