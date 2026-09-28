package ca.bradj.questown.loader;

import ca.bradj.questown.Questown;
import ca.bradj.questown.loader.neo.NeoConfigBuilder;
import ca.bradj.questown.loader.neo.NeoItemHandler;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A {@link LoaderApi} implementation for plain unit tests: no mod environment, no buses,
 * no networking. Registries hand out holders that build their value on first {@code get()}
 * (mirroring a DeferredHolder before registration), config reads its defaults, and the
 * network side is deliberately a no-op - tests that need delivery should test the message
 * classes over the buffer, not the transport.
 */
public final class TestLoader implements LoaderApi {

    // ---- Distribution -----------------------------------------------------
    @Override
    public boolean isClient() {
        return false;
    }

    @Override
    public void runClient(Runnable r) {
    }

    @Override
    public void runServer(Runnable r) {
        r.run();
    }

    // ---- Config -----------------------------------------------------------
    @Override
    public ConfigBuilder configBuilder() {
        return new NeoConfigBuilder();
    }

    @Override
    public void registerConfig(Loader.ConfigType type, ConfigSpec spec, String filename) {
        // Tests read defaults directly; nothing to save.
    }

    // ---- Registries -------------------------------------------------------
    @Override
    public HolderRegistry<Item> items() {
        return new TestHolderRegistry<>();
    }

    @Override
    public HolderRegistry<Block> blocks() {
        return new TestHolderRegistry<>();
    }

    @Override
    public HolderRegistry<MenuType<?>> menuTypes() {
        return new TestHolderRegistry<>();
    }

    @Override
    public HolderRegistry<EntityType<?>> entityTypes() {
        return new TestHolderRegistry<>();
    }

    @Override
    public <T> HolderRegistry<T> registry(ResourceKey<Registry<T>> key) {
        return new TestHolderRegistry<>();
    }

    @Override
    public <T extends AbstractContainerMenu>
    Holder<MenuType<T>> registerMenu(String name, MenuFactory<T> factory) {
        MenuType<T> type = IMenuTypeExtension.create(factory::create);
        return new Fixed<>(type, new ResourceLocation(Questown.MODID, name));
    }

    @Override
    public <T extends AbstractContainerMenu>
    void registerScreen(MenuType<T> menuType, ScreenFactory<T> screenFactory) {
    }

    @Override
    public void defineRegistries() {
        // Plain holders need no bus binding.
    }

    // ---- Id lookups -------------------------------------------------------
    @Override
    public net.minecraft.world.item.@Nullable Item itemFromId(ResourceLocation id) {
        // The item registry is defaulted: .get() returns AIR for a missing key, not null.
        return BuiltInRegistries.ITEM.containsKey(id) ? BuiltInRegistries.ITEM.get(id) : null;
    }

    @Override
    public net.minecraft.resources.@Nullable ResourceLocation itemIdOf(Item item) {
        return BuiltInRegistries.ITEM.getKey(item);
    }

    @Override
    public SoundEvent soundFromId(ResourceLocation id) {
        return BuiltInRegistries.SOUND_EVENT.get(id);
    }

    @Override
    public net.minecraft.resources.@Nullable ResourceLocation blockIdOf(Block block) {
        return BuiltInRegistries.BLOCK.getKey(block);
    }

    @Override
    public net.minecraft.world.level.block.@Nullable Block blockFromId(ResourceLocation id) {
        return BuiltInRegistries.BLOCK.containsKey(id) ? BuiltInRegistries.BLOCK.get(id) : null;
    }

    @Override
    public net.minecraft.resources.@Nullable ResourceLocation biomeKeyOf(net.minecraft.core.Holder<Biome> biome) {
        return biome.unwrapKey().map(ResourceKey::location).orElse(null);
    }

    // ---- Item handlers ----------------------------------------------------
    @Override
    public Optional<ItemHandler> handlerOfEntity(Entity e) {
        return Optional.empty();
    }

    @Override
    public Optional<ItemHandler> handlerOfBlockEntity(BlockEntity b) {
        return Optional.empty();
    }

    @Override
    public ItemHandler handlerForInventory(Container inv) {
        return new NeoItemHandler(new InvWrapper(inv));
    }

    // ---- Open screen ------------------------------------------------------
    @Override
    public void openScreen(ServerPlayer p, MenuProvider provider, @Nullable Consumer<RegistryFriendlyByteBuf> extra) {
    }

    // ---- Networking (no transport in a plain JVM) -------------------------
    @Override
    public Channel channel(String id) {
        return new NoopChannel();
    }

    @Override
    public <T> void sendToPlayer(Player p, T msg) {
    }

    @Override
    public <T> void sendToAll(Level level, T msg) {
    }

    @Override
    public <T> void sendToServer(T msg) {
    }

    // ---- Lifecycle --------------------------------------------------------
    @Override
    public void enqueueWork(Runnable work) {
        work.run();
    }

    @Override
    public void registerCommonHandlers(Object... handlers) {
    }

    // ---- Support types ----------------------------------------------------

    /** A holder that returns one fixed value (already built). */
    private static final class Fixed<T> implements Holder<T> {
        private final T value;
        private final ResourceLocation id;

        Fixed(T value, ResourceLocation id) {
            this.value = value;
            this.id = id;
        }

        @Override
        public T get() {
            return value;
        }

        @Override
        public ResourceLocation id() {
            return id;
        }
    }

    /** A holder that builds and caches its value on first {@code get()}. */
    private static final class Lazy<T> implements Holder<T> {
        private final ResourceLocation id;
        private final Supplier<T> supplier;
        @Nullable
        private T value;
        private boolean built;

        Lazy(ResourceLocation id, Supplier<T> supplier) {
            this.id = id;
            this.supplier = supplier;
        }

        @Override
        public synchronized T get() {
            if (!built) {
                value = supplier.get();
                built = true;
            }
            return value;
        }

        @Override
        public ResourceLocation id() {
            return id;
        }
    }

    /** A registry that stores holders and serves them back, loader-free. */
    public static final class TestHolderRegistry<T> implements HolderRegistry<T> {
        private final List<Holder<? extends T>> holders = new ArrayList<>();

        @Override
        public <I extends T> Holder<I> register(String name, Supplier<I> value) {
            Holder<I> holder = new Lazy<>(new ResourceLocation(Questown.MODID, name), value::get);
            holders.add(holder);
            return holder;
        }

        @Override
        public <I extends T> Holder<I> register(String name, Function<ResourceLocation, I> value) {
            ResourceLocation id = new ResourceLocation(Questown.MODID, name);
            Holder<I> holder = new Lazy<>(id, () -> value.apply(id));
            holders.add(holder);
            return holder;
        }

        @Override
        public Collection<Holder<? extends T>> entries() {
            return List.copyOf(holders);
        }
    }

    /** Registration is accepted, sends vanish. */
    private static final class NoopChannel implements Channel {
        @Override
        public <T> Builder<T> message(Class<T> type, Direction dir) {
            return new Builder<>() {
                @Override
                public Builder<T> encoder(Encoder<T> encoder) {
                    return this;
                }

                @Override
                public Builder<T> decoder(Decoder<T> decoder) {
                    return this;
                }

                @Override
                public Builder<T> handler(Handler<T> handler) {
                    return this;
                }
            };
        }

        @Override
        public <T> void sendToPlayer(Player p, T msg) {
        }

        @Override
        public <T> void sendToAll(Level level, T msg) {
        }

        @Override
        public <T> void sendToServer(T msg) {
        }
    }
}
