package ca.bradj.questown.loader.forge;

import ca.bradj.questown.loader.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

/** Forge 1.19.2 {@link Holder}: a {@link RegistryObject}. */
public final class ForgeHolder<T> implements Holder<T> {
    private final RegistryObject<? extends T> holder;

    public ForgeHolder(RegistryObject<? extends T> holder) {
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
