package ca.bradj.questown.loader;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * A neutral handle to a lazily-registered vanilla registry value (e.g. an {@code Item},
 * {@code EntityType}, {@code Block}). The concrete backing is provided by the active loader
 * (see {@link Loader} / {@code NeoLoader}); main code only deals in this interface.
 *
 * <p>Drop-in for the old Forge {@code RegistryObject}: {@code ItemsInit.FOO.get()} still works,
 * and {@link #id()} replaces {@code RegistryObject.getId()}{@code /getKey()}.
 */
public interface Holder<T> {
    @Nullable
    T get();

    ResourceLocation id();
}
