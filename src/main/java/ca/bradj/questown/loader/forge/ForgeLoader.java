package ca.bradj.questown.loader.forge;

import ca.bradj.questown.core.init.RegistryDefinitions;
import ca.bradj.questown.loader.Channel;
import ca.bradj.questown.loader.ConfigBuilder;
import ca.bradj.questown.loader.ConfigSpec;
import ca.bradj.questown.loader.Holder;
import ca.bradj.questown.loader.HolderRegistry;
import ca.bradj.questown.loader.ItemHandler;
import ca.bradj.questown.loader.Loader;
import ca.bradj.questown.loader.LoaderApi;
import ca.bradj.questown.loader.MenuFactory;
import net.minecraft.core.Registry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * The Forge 1.19.2 impl of the neutral loader seam.
 * Only this package (plus the {@code @Mod} entry) names loader types; main code talks to {@link Loader}.
 */
public final class ForgeLoader implements LoaderApi {

    private static final String MODID = "questown";
    private static final String CHANNEL_ID = "questown:network";

    private final List<ForgeHolderRegistry<?>> createdRegistries = new CopyOnWriteArrayList<>();
    private boolean registriesBound = false;

    /** Menu-screen registrations captured client-side; the client island replays them. */
    private final List<PendingScreen<?>> pendingScreens = new CopyOnWriteArrayList<>();

    public record PendingScreen<T extends AbstractContainerMenu>(MenuType<T> menuType, LoaderApi.ScreenFactory<T> factory) {
    }

    public List<PendingScreen<?>> pendingScreens() {
        return pendingScreens;
    }

    // ---- Distribution -----------------------------------------------------
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

    // ---- Config -----------------------------------------------------------
    @Override
    public ConfigBuilder configBuilder() {
        return new ForgeConfigBuilder();
    }

    @Override
    public void registerConfig(Loader.ConfigType type, ConfigSpec spec, String filename) {
        ModConfig.Type mapped = switch (type) {
            case SERVER -> ModConfig.Type.SERVER;
            case CLIENT -> ModConfig.Type.CLIENT;
            case COMMON, NORMAL -> ModConfig.Type.COMMON;
        };
        ModLoadingContext.get().registerConfig(mapped, ((ForgeConfigSpecHandle) spec).spec(), filename);
    }

    // ---- Registries -------------------------------------------------------
    @Override
    public HolderRegistry<net.minecraft.world.item.Item> items() {
        return track(DeferredRegister.create(ForgeRegistries.ITEMS, MODID));
    }

    @Override
    public HolderRegistry<net.minecraft.world.level.block.Block> blocks() {
        return track(DeferredRegister.create(ForgeRegistries.BLOCKS, MODID));
    }

    @Override
    public HolderRegistry<MenuType<?>> menuTypes() {
        return track(DeferredRegister.create(ForgeRegistries.MENU_TYPES, MODID));
    }

    @Override
    public HolderRegistry<net.minecraft.world.entity.EntityType<?>> entityTypes() {
        return track(DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MODID));
    }

    @Override
    public <T> HolderRegistry<T> registry(ResourceKey<Registry<T>> key) {
        return track(DeferredRegister.create(key, MODID));
    }

    @Override
    public <T extends AbstractContainerMenu> Holder<MenuType<T>> registerMenu(String name, MenuFactory<T> factory) {
        DeferredRegister<MenuType<?>> reg = DeferredRegister.create(ForgeRegistries.MENU_TYPES, MODID);
        createdRegistries.add(new ForgeHolderRegistry<>(reg, MODID));
        MenuType<T> type = IForgeMenuType.create(factory::create);
        RegistryObject<MenuType<T>> holder = reg.register(name, () -> type);
        return new ForgeHolder<>(holder);
    }

    private <T> HolderRegistry<T> track(DeferredRegister<T> reg) {
        ForgeHolderRegistry<T> reg2 = new ForgeHolderRegistry<>(reg, MODID);
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
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        for (ForgeHolderRegistry<?> reg : createdRegistries) {
            reg.bind(bus);
        }
        ca.bradj.questown.QT.LOGGER.info("[questown] bound {} deferred registries to the mod bus", createdRegistries.size());
    }

    @Override
    public net.minecraft.world.item.@Nullable Item itemFromId(ResourceLocation id) {
        // The item registry is defaulted: get() returns AIR for a missing key, not null.
        return Registry.ITEM.containsKey(id) ? Registry.ITEM.get(id) : null;
    }

    @Override
    public @Nullable ResourceLocation itemIdOf(net.minecraft.world.item.Item item) {
        return Registry.ITEM.getKey(item);
    }

    @Override
    public @Nullable ResourceLocation blockIdOf(net.minecraft.world.level.block.Block block) {
        return Registry.BLOCK.getKey(block);
    }

    @Override
    public net.minecraft.world.level.block.@Nullable Block blockFromId(ResourceLocation id) {
        return Registry.BLOCK.containsKey(id) ? Registry.BLOCK.get(id) : null;
    }

    @Override
    public net.minecraft.sounds.SoundEvent soundFromId(ResourceLocation id) {
        return Registry.SOUND_EVENT.get(id);
    }

    @Override
    public @Nullable ResourceLocation biomeKeyOf(net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome) {
        return biome.unwrapKey().map(ResourceKey::location).orElse(null);
    }

    // ---- Item handlers ----------------------------------------------------
    @Override
    public Optional<ItemHandler> handlerOfEntity(Entity e) {
        return Optional.ofNullable(handler(e)).map(ForgeItemHandler::new);
    }

    @Override
    public Optional<ItemHandler> handlerOfBlockEntity(BlockEntity b) {
        return Optional.ofNullable(handler(b)).map(ForgeItemHandler::new);
    }

    @Override
    public ItemHandler handlerForInventory(Container inv) {
        return new ForgeItemHandler(new InvWrapper(inv));
    }

    @Nullable
    private IItemHandler handler(Object o) {
        if (o instanceof IItemHandler ih) {
            return ih;
        }
        IItemHandler h = null;
        if (o instanceof BlockEntity be) {
            h = be.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElse(null);
        } else if (o instanceof Entity e) {
            h = e.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElse(null);
        }
        if (h == null) {
            ca.bradj.questown.QT.LOGGER.warn("[questown] no item-handler capability for " + (o == null ? "null" : o.getClass().getSimpleName()));
        }
        return h;
    }

    // ---- Open screen ------------------------------------------------------
    @Override
    public void openScreen(ServerPlayer p, MenuProvider provider, @Nullable Consumer<FriendlyByteBuf> extra) {
        NetworkHooks.openScreen(p, provider, extra == null ? buf -> {
        } : extra);
    }

    // ---- Networking -------------------------------------------------------
    private ForgeChannel forgeChannel;

    @Override
    public synchronized Channel channel(String id) {
        if (forgeChannel == null) {
            forgeChannel = new ForgeChannel(id);
        }
        return forgeChannel;
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

    // ---- Lifecycle --------------------------------------------------------
    @Override
    public void enqueueWork(Runnable work) {
        work.run();
    }

    @Override
    public void registerCommonHandlers(Object... handlers) {
        for (Object handler : handlers) {
            MinecraftForge.EVENT_BUS.register(handler);
        }
    }

    @Override
    public <T extends AbstractContainerMenu> void registerScreen(MenuType<T> menuType, LoaderApi.ScreenFactory<T> screenFactory) {
        pendingScreens.add(new PendingScreen<>(menuType, screenFactory));
    }
}
