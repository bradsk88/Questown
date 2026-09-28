package ca.bradj.questown.loader;

import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A loader registry the mod owns. Backed by
 * <ul>
 *   <li>NeoForge: a {@code DeferredRegister} (items/blocks/entities/menus or a named key).</li>
 *   <li>Forge: the same, via that loader's equivalent.</li>
 * </ul>
 * Main code holds these fields (e.g. {@code public static final HolderRegistry<Item> ITEMS = Loader.items();})
 * and {@code .register(name, supplier)}s entries, reading the concrete through the returned {@link Holder}.
 */
public interface HolderRegistry<T> {
    <I extends T> Holder<I> register(String name, Supplier<I> value);

    <I extends T> Holder<I> register(String name, Function<ResourceLocation, I> value);

    /** All holders registered so far, in registration order. */
    Collection<Holder<? extends T>> entries();
}
