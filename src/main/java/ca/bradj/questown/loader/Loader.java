package ca.bradj.questown.loader;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.inventory.AbstractInventory;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * The single loader seam for the whole mod. Main code (including {@code Compat}) calls these static,
 * neutral methods and never names a loader type. All loader-specific machinery is delegated to a
 * {@link LoaderApi} installed once at mod start (for NeoForge: {@code loader.neo.NeoLoader}).
 *
 * <p>Strangler: the mod's code only talks to this neutral surface, so the loader underneath it
 * (NeoForge 1.20.6 primary, a legacy Forge 1.19.2 api) can be swapped behind it. Every method here uses
 * vanilla ({@code net.minecraft}) or {@code ca.bradj.questown.loader} types, so main code carries zero
 * loader imports.
 */
public final class Loader {

    public enum ConfigType { SERVER, CLIENT, COMMON, NORMAL }

    private static final AtomicReference<LoaderApi> API = new AtomicReference<>();

    private Loader() {
    }

    public static void init(LoaderApi api) {
        API.set(api);
    }

    private static LoaderApi api() {
        LoaderApi api = API.get();
        if (api == null) {
            throw new IllegalStateException("No loader installed (Loader.init not called); got here before mod start.");
        }
        return api;
    }

    // ---- Distribution -----------------------------------------------------
    public static boolean isClient() {
        return api().isClient();
    }

    public static void runClient(Runnable r) {
        api().runClient(r);
    }

    public static void runServer(Runnable r) {
        api().runServer(r);
    }

    // ---- Config -----------------------------------------------------------
    public static ConfigBuilder configBuilder() {
        return api().configBuilder();
    }

    public static void registerConfig(ConfigType type, ConfigSpec spec, String filename) {
        api().registerConfig(type, spec, filename);
    }

    // ---- Registries -------------------------------------------------------
    public static HolderRegistry<net.minecraft.world.item.Item> items() {
        return api().items();
    }

    public static HolderRegistry<net.minecraft.world.level.block.Block> blocks() {
        return api().blocks();
    }

    public static HolderRegistry<net.minecraft.world.MenuType<?>> menuTypes() {
        return api().menuTypes();
    }

    public static HolderRegistry<net.minecraft.world.entity.EntityType<?>> entityTypes() {
        return api().entityTypes();
    }

    public static <T> HolderRegistry<T> registry(ResourceKey<Registry<T>> key) {
        return api().registry(key);
    }

    /** Neutral menu registration: the loader wraps the mod-owned factory with its own container type. */
    public static <T extends net.minecraft.world.inventory.AbstractContainerMenu>
    Holder<net.minecraft.world.MenuType<T>> registerMenu(String name, MenuFactory<T> factory) {
        return api().registerMenu(name, factory);
    }

    /** Force the {@code *Init} classes' static registries to be created; called from the mod entry. */
    public static void defineRegistries() {
        api().defineRegistries();
    }

    public static net.minecraft.world.item.@Nullable Item itemFromId(ResourceLocation id) {
        return api().itemFromId(id);
    }

    public static net.minecraft.resources.@Nullable ResourceLocation itemIdOf(net.minecraft.world.item.Item item) {
        return api().itemIdOf(item);
    }

    public static net.minecraft.resources.@Nullable ResourceLocation blockIdOf(net.minecraft.world.level.block.Block block) {
        return api().blockIdOf(block);
    }

    public static net.minecraft.world.level.block.Block blockFromId(ResourceLocation id) {
        return api().blockFromId(id);
    }

    public static net.minecraft.world.level.SoundEvent soundFromId(ResourceLocation id) {
        return api().soundFromId(id);
    }

    // ---- Item handlers (capabilities) -------------------------------------
    public static Optional<ItemHandler> handlerOf(Entity e) {
        return api().handlerOfEntity(e);
    }

    public static Optional<ItemHandler> handlerOf(BlockEntity b) {
        return api().handlerOfBlockEntity(b);
    }

    public static ItemHandler handlerForInventory(AbstractInventory inv) {
        return api().handlerForInventory(inv);
    }

    public static boolean isEmpty(ItemHandler h, int slot) {
        return h.getStackInSlot(slot).isEmpty();
    }

    // ---- Open screen ------------------------------------------------------
    public static void openScreen(ServerPlayer p, MenuProvider provider, @Nullable Consumer<RegistryFriendlyByteBuf> extra) {
        api().openScreen(p, provider, extra);
    }

    // ---- Networking -------------------------------------------------------
    public static Channel channel(String id) {
        return api().channel(id);
    }

    public static <T> void sendToPlayer(Player p, T msg) {
        api().sendToPlayer(p, msg);
    }

    public static <T> void sendToServer(T msg) {
        api().sendToServer(msg);
    }

    // ---- Lifecycle --------------------------------------------------------
    public static void enqueueWork(Runnable work) {
        api().enqueueWork(work);
    }

    public static void registerCommonHandlers(Object... handlers) {
        api().registerCommonHandlers(handlers);
    }
}
