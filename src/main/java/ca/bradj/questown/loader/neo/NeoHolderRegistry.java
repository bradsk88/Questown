package ca.bradj.questown.loader.neo;

import ca.bradj.questown.loader.Holder;
import ca.bradj.questown.loader.HolderRegistry;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

public final class NeoHolderRegistry<T> implements HolderRegistry<T> {
    private final DeferredRegister<T> deferred;

    public NeoHolderRegistry(DeferredRegister<T> deferred) {
        this.deferred = deferred;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <I extends T> Holder<I> register(String name, Supplier<I> value) {
        return (Holder<I>) (Holder<?>) new NeoHolder<>(deferred.register(name, value));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <I extends T> Holder<I> register(String name, Function<ResourceLocation, I> value) {
        return (Holder<I>) (Holder<?>) new NeoHolder<>(deferred.register(name, value));
    }

    @Override
    public Collection<Holder<? extends T>> entries() {
        List<Holder<? extends T>> out = new ArrayList<>();
        for (DeferredHolder<T, ? extends T> holder : deferred.getEntries()) {
            out.add(new NeoHolder<>(holder));
        }
        return out;
    }

    public void bind(IEventBus modBus) {
        deferred.register(modBus);
    }
}
