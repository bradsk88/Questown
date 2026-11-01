package ca.bradj.questown.loader;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/** A loader-neutral handle to a lazily registered registry value (an item, block, entity type, ...). */
public interface Holder<T> {
    @Nullable
    T get();

    ResourceLocation id();
}
