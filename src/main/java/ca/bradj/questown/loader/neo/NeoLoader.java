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
// ConfigType is Loader.ConfigType (defined in the neutral facade).
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
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.Container;
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
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.Nullable;

/**
 * The NeoForge 1.20.6 impl of the neutral loader seam. Only this class (plus the per-loader {@code @Mod} entry)
 * may name loader types; main code only talks to the neutral {@code Loader}.
 *
 * <p>Implemented against the confirmed 1.20.6 API: config via {@code ModConfigSpec} + {@code ModLoadingContext},
 * registries via {@code DeferredRegister}/{@code DeferredHolder} bound to the mod bus, distribution via
 * {@code FMLEnvironment.dist}/{@code Dist}, open screen via vanilla {@code ServerPlayer.openMenu}, common
 * events via {@code NeoForge.EVENT_BUS}.
 *
 * <p>Still to complete (the remaining migration slice): the Neo Payload network stack
 * ({@link #channel}/{@link #sendToPlayer}/{@link #sendToServer}) and the redesigned item-handler capability
 * lookup ({@link #handlerOfEntity}/{@link #handlerOfBlockEntity}). Those throw a marked error rather than
 * silently misbehaving.
 */
public final class NeoLoader implements LoaderApi {

    private final List<NeoHolderRegistry<?>> createdRegistries = new CopyOnWriteArrayList<>();
    private boolean registriesBound = false;

    private static final String MODID = "questown";

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
        return new NeoConfigBuilder();
    }

    @Override
    public void registerConfig(Loader.ConfigType type, ConfigSpec spec, String filename) {
        NeoConfigSpec neoSpec = (NeoConfigSpec) spec;
        ModConfig.Type mapped = switch (type) {
            case SERVER -> ModConfig.Type.SERVER;
            case CLIENT -> ModConfig.Type.CLIENT;
            case COMMON -> ModConfig.Type.COMMON;
            case NORMAL -> ModConfig.Type.NORMAL;
        };
        ModLoadingContext.get().registerConfig(mapped, neoSpec.modSpec(), filename);
    }

    // ---- Registries -------------------------------------------------------
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
        return track(DeferredRegister.create(Registries.MENU_TYPES, MODID));
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
        DeferredRegister<net.minecraft.world.inventory.MenuType<?>> reg = DeferredRegister.create(Registries.MENU_TYPES, MODID);
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
        return BuiltInRegistries.ITEM.get(id);
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
    public net.minecraft.world.level.block.Block blockFromId(ResourceLocation id) {
        return BuiltInRegistries.BLOCK.get(id);
    }

    @Override
    public net.minecraft.sounds.SoundEvent soundFromId(ResourceLocation id) {
        return net.minecraft.core.Registry.BuiltInRegistries.SOUND_EVENT.get(id);
    }

    @Override
    public net.minecraft.resources.@Nullable ResourceLocation biomeKeyOf(net.minecraft.world.level.biome.Biome biome) {
        return BuiltInRegistries.BIOME.getKey(biome);
    }

    // ---- Item handlers ----------------------------------------------------
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
        IItemHandler h = o instanceof IItemHandler ih ? ih : null;
        if (h != null) {
            return h;
        }
        ca.bradj.questown.QT.LOGGER.warn("[questown] item-handler capability lookup not yet implemented for NeoForge; returning none ("+(o == null ? "null" : o.getClass().getSimpleName())+")");
        return null;
    }

    // ---- Open screen ------------------------------------------------------
    @Override
    public void openScreen(ServerPlayer p, MenuProvider provider, @Nullable Consumer<RegistryFriendlyByteBuf> extra) {
        p.openMenu(provider, extra == null ? buf -> {
        } : extra);
    }

    // ---- Networking (NOT YET IMPLEMENTED for Neo 1.20.6) ------------------
    @Override
    public Channel channel(String id) {
        throw notYet("network channel(" + id + ")");
    }

    @Override
    public <T> void sendToPlayer(Player p, T msg) {
        ca.bradj.questown.QT.LOGGER.warn("[questown] network sendToPlayer not yet implemented for NeoForge; dropping "+msg.getClass().getSimpleName()+".");
    }

    @Override
    public <T> void sendToServer(T msg) {
        ca.bradj.questown.QT.LOGGER.warn("[questown] network sendToServer not yet implemented for NeoForge; dropping "+msg.getClass().getSimpleName()+".");
    }

    // ---- Lifecycle --------------------------------------------------------
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

    private static IllegalStateException notYet(String what) {
        return new IllegalStateException(
                "NeoLoader: " + what + " is not implemented for NeoForge 1.20.6 yet (network / capability = remaining migration slice).");
    }
}
