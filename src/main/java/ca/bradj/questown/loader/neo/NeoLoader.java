package ca.bradj.questown.loader.neo;

import ca.bradj.questown.core.init.RegistryDefinitions;
import ca.bradj.questown.loader.Channel;
import ca.bradj.questown.loader.ConfigBuilder;
import ca.bradj.questown.loader.ConfigSpec;
import ca.bradj.questown.loader.Holder;
import ca.bradj.questown.loader.HolderRegistry;
import ca.bradj.questown.loader.ItemHandler;
import ca.bradj.questown.loader.Loader;
import ca.bradj.questown.loader.MenuFactory;
import ca.bradj.questown.loader.LoaderApi;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.registries.Registries;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The NeoForge 1.20.6 {@link LoaderApi}: config, registries, distribution, screens, networking and item-handler capabilities. Only this package names NeoForge types. */
public final class NeoLoader implements LoaderApi {

    static final Logger LOGGER = LoggerFactory.getLogger(NeoLoader.class);


    private final List<NeoHolderRegistry<?>> createdRegistries = new CopyOnWriteArrayList<>();
    private boolean registriesBound = false;

    /** Menu screens to register on the client. */
    private final List<PendingScreen> pendingScreens = new CopyOnWriteArrayList<>();

    public record PendingScreen<T extends net.minecraft.world.inventory.AbstractContainerMenu>(
            net.minecraft.world.inventory.MenuType<T> menuType, LoaderApi.ScreenFactory<T> factory) {
    }

    public List<PendingScreen> pendingScreens() {
        return pendingScreens;
    }


    private static final String MODID = "questown";

    @Override
    public boolean isClient() {
        return FMLEnvironment.dist == Dist.CLIENT;
    }

    @Override
    public void runClient(Runnable r) {
        if (isClient()) {
            r.run();
        }
    }

    @Override
    public void runServer(Runnable r) {
        if (!isClient()) {
            r.run();
        }
    }

    @Override
    public ConfigBuilder configBuilder() {
        return new NeoConfigBuilder();
    }

    @Override
    public void registerConfig(Loader.ConfigType type, ConfigSpec spec, String filename) {
        NeoConfigSpec neoSpec = (NeoConfigSpec) spec;
        ModConfig.Type mapped = switch (type) {
            case SERVER -> ModConfig.Type.SERVER;
            case CLIENT -> ModConfig.Type.CLIENT;
            case COMMON -> ModConfig.Type.COMMON;
            case NORMAL -> ModConfig.Type.COMMON;
        };
        ModLoadingContext.get().registerConfig(mapped, neoSpec.modSpec(), filename);
    }

    @Override
    public HolderRegistry<net.minecraft.world.item.Item> items() {
        return track(DeferredRegister.createItems(MODID));
    }

    @Override
    public HolderRegistry<net.minecraft.world.level.block.Block> blocks() {
        return track(DeferredRegister.createBlocks(MODID));
    }

    @Override
    public HolderRegistry<net.minecraft.world.inventory.MenuType<?>> menuTypes() {
        return track(DeferredRegister.create(Registries.MENU, MODID));
    }

    @Override
    public HolderRegistry<net.minecraft.world.entity.EntityType<?>> entityTypes() {
        return track(DeferredRegister.create(Registries.ENTITY_TYPE, MODID));
    }

    @Override
    public <T> HolderRegistry<T> registry(ResourceKey<Registry<T>> key) {
        return track(DeferredRegister.create(key, MODID));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends net.minecraft.world.inventory.AbstractContainerMenu>
    Holder<net.minecraft.world.inventory.MenuType<T>> registerMenu(String name, MenuFactory<T> factory) {
        DeferredRegister<net.minecraft.world.inventory.MenuType<?>> reg = DeferredRegister.create(Registries.MENU, MODID);
        createdRegistries.add(new NeoHolderRegistry<>(reg));
        net.neoforged.neoforge.network.IContainerFactory<T> container = factory::create;
        net.minecraft.world.inventory.MenuType<T> type =
                net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(container);
        net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.inventory.MenuType<?>, net.minecraft.world.inventory.MenuType<T>> holder
                = reg.register(name, () -> type);
        return new NeoHolder<net.minecraft.world.inventory.MenuType<T>>(
                (net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.inventory.MenuType<T>, net.minecraft.world.inventory.MenuType<T>>) (Object) holder
        );
    }

    private <T> HolderRegistry<T> track(DeferredRegister<T> reg) {
        NeoHolderRegistry<T> reg2 = new NeoHolderRegistry<>(reg);
        createdRegistries.add(reg2);
        return reg2;
    }

    @Override
    public void defineRegistries() {
        RegistryDefinitions.define();
        if (registriesBound) {
            return;
        }
        registriesBound = true;
        IEventBus bus = ModLoadingContext.get().getActiveContainer().getEventBus();
        for (NeoHolderRegistry<?> reg : createdRegistries) {
            bind((NeoHolderRegistry<Object>) reg, bus);
        }
        ca.bradj.questown.QT.LOGGER.info("[questown] bound {} deferred registries to the mod bus", createdRegistries.size());
    }

    private void bind(NeoHolderRegistry<Object> reg, IEventBus bus) {
        reg.bind(bus);
    }

    @Override
    public net.minecraft.world.item.@Nullable Item itemFromId(ResourceLocation id) {
        return BuiltInRegistries.ITEM.containsKey(id) ? BuiltInRegistries.ITEM.get(id) : null;
    }

    @Override
    public net.minecraft.resources.@Nullable ResourceLocation itemIdOf(net.minecraft.world.item.Item item) {
        return BuiltInRegistries.ITEM.getKey(item);
    }

    @Override
    public net.minecraft.resources.@Nullable ResourceLocation blockIdOf(net.minecraft.world.level.block.Block block) {
        return BuiltInRegistries.BLOCK.getKey(block);
    }

    @Override
    public net.minecraft.world.level.block.@Nullable Block blockFromId(ResourceLocation id) {
        return BuiltInRegistries.BLOCK.containsKey(id) ? BuiltInRegistries.BLOCK.get(id) : null;
    }

    @Override
    public net.minecraft.sounds.SoundEvent soundFromId(ResourceLocation id) {
        return BuiltInRegistries.SOUND_EVENT.get(id);
    }

    @Override
    public net.minecraft.resources.@Nullable ResourceLocation biomeKeyOf(net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome) {
        return biome.unwrapKey().map(net.minecraft.resources.ResourceKey::location).orElse(null);
    }

    @Override
    public Optional<ItemHandler> handlerOfEntity(Entity e) {
        return Optional.ofNullable(handler(e)).map(NeoItemHandler::new);
    }

    @Override
    public Optional<ItemHandler> handlerOfBlockEntity(BlockEntity b) {
        return Optional.ofNullable(handler(b)).map(NeoItemHandler::new);
    }

    @Override
    public ItemHandler handlerForInventory(Container inv) {
        return new NeoItemHandler(new InvWrapper(inv));
    }

    @Nullable
    private IItemHandler handler(Object o) {
        if (o instanceof IItemHandler ih) {
            return ih;
        }
        // Containers and entity inventories register an item-handler capability; null means the default side.
        IItemHandler h = null;
        if (o instanceof BlockEntity be) {
            h = net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK
                    .getCapability(be.getLevel(), be.getBlockPos(), be.getBlockState(), be, null);
        } else if (o instanceof Entity e) {
            h = net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.ENTITY.getCapability(e, null);
        }
        if (h == null) {
            ca.bradj.questown.QT.LOGGER.warn("[questown] no item-handler capability for "+(o == null ? "null" : o.getClass().getSimpleName()));
            return null;
        }
        return h;
    }

    @Override
    public void openScreen(ServerPlayer p, MenuProvider provider, @Nullable Consumer<net.minecraft.network.FriendlyByteBuf> extra) {
        p.openMenu(provider, buf -> {
            if (extra != null) {
                extra.accept(buf);
            }
        });
    }

    private NeoChannel neoChannel;
    private static final String CHANNEL_ID = "questown:network";

    @Override
    public synchronized Channel channel(String id) {
        if (neoChannel == null) {
            neoChannel = new NeoChannel(id);
        }
        return neoChannel;
    }

    public synchronized NeoChannel channelObj() {
        if (neoChannel == null) {
            neoChannel = new NeoChannel(CHANNEL_ID);
        }
        return neoChannel;
    }

    @Override
    public <T> void sendToPlayer(Player p, T msg) {
        channel(CHANNEL_ID).sendToPlayer(p, msg);
    }

    @Override
    public <T> void sendToAll(Level level, T msg) {
        channel(CHANNEL_ID).sendToAll(level, msg);
    }

    @Override
    public <T> void sendToServer(T msg) {
        channel(CHANNEL_ID).sendToServer(msg);
    }

    void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        if (neoChannel != null) {
            neoChannel.onRegisterPayloadHandlers(event);
        }
    }

    @Override
    public void enqueueWork(Runnable work) {
        work.run();
    }

    @Override
    public void registerCommonHandlers(Object... handlers) {
        for (Object handler : handlers) {
            NeoForge.EVENT_BUS.register(handler);
        }
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public <T extends AbstractContainerMenu> void registerScreen(
            MenuType<T> menuType,
            LoaderApi.ScreenFactory<T> screenFactory
    ) {
        pendingScreens.add(new PendingScreen(menuType, screenFactory));
    }

    private static IllegalStateException notYet(String what) {
        return new IllegalStateException(
                "NeoLoader: " + what + " is not implemented for NeoForge 1.20.6 yet (network / capability = remaining migration slice).");
    }
}
