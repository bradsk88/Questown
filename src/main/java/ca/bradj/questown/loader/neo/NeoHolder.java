package ca.bradj.questown.loader.neo;

import ca.bradj.questown.loader.Holder;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.jetbrains.annotations.Nullable;

/** NeoForge impl of the neutral {@link Holder}; wraps a {@code DeferredHolder}. */
public final class NeoHolder<T> implements Holder<T> {
    private final DeferredHolder<T, ? extends T> holder;

    public NeoHolder(DeferredHolder<T, ? extends T> holder) {
        this.holder = holder;
    }

    @Nullable
    @Override
    public T get() {
        return holder.get();
    }

    @Override
    public ResourceLocation id() {
        return holder.getId();
    }
}
