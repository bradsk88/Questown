package ca.bradj.questown.loader.forge;

import ca.bradj.questown.loader.Holder;
import ca.bradj.questown.loader.HolderRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/** Forge 1.19.2 {@link HolderRegistry}: a {@link DeferredRegister} bound to the mod bus. */
public final class ForgeHolderRegistry<T> implements HolderRegistry<T> {
    private final DeferredRegister<T> deferred;
    private final String namespace;

    public ForgeHolderRegistry(DeferredRegister<T> deferred, String namespace) {
        this.deferred = deferred;
        this.namespace = namespace;
    }

    @Override
    public <I extends T> Holder<I> register(String name, Supplier<I> value) {
        return new ForgeHolder<>(deferred.register(name, value));
    }

    @Override
    public <I extends T> Holder<I> register(String name, Function<ResourceLocation, I> value) {
        // Forge 1.19.2's DeferredRegister takes a Supplier only; the id is known up front.
        ResourceLocation id = new ResourceLocation(namespace, name);
        return new ForgeHolder<>(deferred.register(name, () -> value.apply(id)));
    }

    @Override
    public Collection<Holder<? extends T>> entries() {
        List<Holder<? extends T>> out = new ArrayList<>();
        for (RegistryObject<T> holder : deferred.getEntries()) {
            out.add(new ForgeHolder<>(holder));
        }
        return out;
    }

    public void bind(IEventBus modBus) {
        deferred.register(modBus);
    }
}
