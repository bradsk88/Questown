package ca.bradj.questown.loader;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.Container;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * The concrete loader seam. {@link Loader} forwards every call to the installed api; a NeoForge build
 * installs {@code NeoLoader}, a legacy Forge build its own. Main code never sees this type directly — it
 * calls {@code Loader} — but the interface is defined in main (neutral) so the api's method signatures are
 * all vanilla/neutral types. The implementations live in a per-loader source set and may import loader types.
 */
public interface LoaderApi {

    // Distribution
    boolean isClient();
    void runClient(Runnable r);
    void runServer(Runnable r);

    // Config
    ConfigBuilder configBuilder();
    void registerConfig(Loader.ConfigType type, ConfigSpec spec, String filename);

    // Registries
    HolderRegistry<net.minecraft.world.item.Item> items();
    HolderRegistry<net.minecraft.world.level.block.Block> blocks();
    HolderRegistry<net.minecraft.world.inventory.MenuType<?>> menuTypes();
    HolderRegistry<net.minecraft.world.entity.EntityType<?>> entityTypes();
    <T> HolderRegistry<T> registry(ResourceKey<Registry<T>> key);

    /** Neutral menu registration: the loader wraps the mod-owned factory with its own container type. */
    <T extends net.minecraft.world.inventory.AbstractContainerMenu>
    Holder<net.minecraft.world.inventory.MenuType<T>> registerMenu(String name, MenuFactory<T> factory);
    /**
     * Neutral menu-screen registration (client only). NeoForge fires its
     * {@code RegisterMenuScreensEvent} at client init — before any mod-bus setup event — so the
     * loader captures these at construction time and replays them when the event arrives.
     */
    /**
     * The neutral shape of a menu screen factory (a vanilla {@code MenuScreens.ScreenConstructor}
     * minus its load-bearing bound): javac refuses to infer a lambda's descriptor against the
     * vanilla interface's {@code U extends Screen & MenuAccess<T>} when T arrives via a wildcard
     * target, so main code hands the loader a plain factory and the loader adapts it.
     */
    /**
     * Neutral menu-screen factory: the return type is {@code Object} so this common interface
     * carries no client class reference; the client island (NeoLoaderClient) knows the result
     * is a vanilla screen and adapts it.
     */
    @FunctionalInterface
    interface ScreenFactory<T extends net.minecraft.world.inventory.AbstractContainerMenu> {
        Object create(
                T menu,
                net.minecraft.world.entity.player.Inventory playerInventory,
                net.minecraft.network.chat.Component title);
    }

    <T extends net.minecraft.world.inventory.AbstractContainerMenu>
    void registerScreen(
            net.minecraft.world.inventory.MenuType<T> menuType,
            ScreenFactory<T> screenFactory);

    /** Touch the {@code *Init} classes so their static registries exist; the api then binds them to the mod bus. */
    void defineRegistries();

    
    
    net.minecraft.world.item.@Nullable Item itemFromId(ResourceLocation id);
    net.minecraft.resources.@Nullable ResourceLocation itemIdOf(net.minecraft.world.item.Item item);
    net.minecraft.sounds.@Nullable SoundEvent soundFromId(ResourceLocation id);
    net.minecraft.resources.@Nullable ResourceLocation blockIdOf(net.minecraft.world.level.block.Block block);
    net.minecraft.world.level.block.@Nullable Block blockFromId(ResourceLocation id);
    net.minecraft.resources.@Nullable ResourceLocation biomeKeyOf(net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome);

    // Item handlers
    Optional<ItemHandler> handlerOfEntity(Entity e);
    Optional<ItemHandler> handlerOfBlockEntity(BlockEntity b);
    ItemHandler handlerForInventory(Container inv);

    // Open screen
    void openScreen(ServerPlayer p, MenuProvider provider, @Nullable Consumer<RegistryFriendlyByteBuf> extra);

    // Networking
    Channel channel(String id);
    <T> void sendToPlayer(Player p, T msg);
    <T> void sendToAll(net.minecraft.world.level.Level level, T msg);
    <T> void sendToServer(T msg);

    // Lifecycle
    void enqueueWork(Runnable work);
    void registerCommonHandlers(Object... handlers);
}
