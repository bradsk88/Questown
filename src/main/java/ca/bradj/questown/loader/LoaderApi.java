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
import net.minecraft.network.FriendlyByteBuf;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * What a mod loader provides, implemented once per loader ({@code loader.forge}, {@code loader.neo})
 * and installed behind {@link Loader}. Its signatures use only vanilla and neutral types.
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
     * A menu-screen factory. It returns {@code Object} so this interface names no client class, and
     * it is not a vanilla {@code ScreenConstructor} because javac cannot infer that interface's
     * {@code Screen & MenuAccess<T>} bound for a wildcard menu type.
     */
    @FunctionalInterface
    interface ScreenFactory<T extends net.minecraft.world.inventory.AbstractContainerMenu> {
        Object create(
                T menu,
                net.minecraft.world.entity.player.Inventory playerInventory,
                net.minecraft.network.chat.Component title);
    }

    /** Client only: the loader captures these and replays them when its screen-registration event fires. */
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
    void openScreen(ServerPlayer p, MenuProvider provider, @Nullable Consumer<FriendlyByteBuf> extra);

    // Networking
    Channel channel(String id);
    <T> void sendToPlayer(Player p, T msg);
    <T> void sendToAll(net.minecraft.world.level.Level level, T msg);
    <T> void sendToServer(T msg);

    // Lifecycle
    void enqueueWork(Runnable work);
    void registerCommonHandlers(Object... handlers);
}
