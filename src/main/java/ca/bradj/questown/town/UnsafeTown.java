package ca.bradj.questown.town;

import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class UnsafeTown {

    private final Class<?> owner;

    @Nullable
    TownFlagBlockEntity town = null;

    public UnsafeTown(Class<?> owner) {
        this.owner = owner;
    }

    public void initialize(TownFlagBlockEntity t) {
        this.town = t;
    }

    // Only safe to call after initialized
    public @NotNull TownFlagBlockEntity getUnsafe() {
        if (town == null) {
            throw new IllegalStateException(String.format("Town has not been initialized on %s yet", owner));
        }
        return town;
    }

    // Only safe to call from the server side
    public @NotNull ServerLevel getServerLevelUnsafe() {
        return getUnsafe().getServerLevel();
    }
}
