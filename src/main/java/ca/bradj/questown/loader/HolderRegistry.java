package ca.bradj.questown.loader;

import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.function.Function;
import java.util.function.Supplier;

/** A registry the mod owns (a {@code DeferredRegister} underneath); entries are read through their {@link Holder}. */
public interface HolderRegistry<T> {
    <I extends T> Holder<I> register(String name, Supplier<I> value);

    <I extends T> Holder<I> register(String name, Function<ResourceLocation, I> value);

    /** All holders registered so far, in registration order. */
    Collection<Holder<? extends T>> entries();
}
