package ca.bradj.questown.loader.neo;

import ca.bradj.questown.loader.Loader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;

/** The client side of the NeoForge loader: the client's send hook, menu-screen registration, and client-only event handlers. A dedicated server never loads it. */
public final class NeoLoaderClient {

    private NeoLoaderClient() {
    }

    public static void init() {
        NeoLoader neo = (NeoLoader) Loader.api();
        neo.channelObj().setClientSend(envelope -> {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            if (player != null) {
                ((ICommonPacketListener) player.connection).send(envelope);
            } else {
                ca.bradj.questown.QT.LOGGER.warn("[questown] sendToServer dropped (no local player)");
            }
        });
        Loader.api().registerCommonHandlers(new NeoMouseScrollDispatch());
    }

    /** Registers the captured menu screens with NeoForge. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        NeoLoader neo = (NeoLoader) Loader.api();
        // This event can come before the setup queue has captured the screens, so capture them here first.
        if (neo.pendingScreens().isEmpty()) {
            ca.bradj.questown.QuestownClient.registerScreens();
        }
        int count = neo.pendingScreens().size();
        for (NeoLoader.PendingScreen<?> p : neo.pendingScreens()) {
            NeoLoaderClient.registerOne(event, (NeoLoader.PendingScreen) p);
        }
        neo.pendingScreens().clear();
        ca.bradj.questown.QT.LOGGER.info("[questown] registered {} menu screens on the client", count);
    }

    private static <T extends AbstractContainerMenu> void registerOne(RegisterMenuScreensEvent event, NeoLoader.PendingScreen<T> p) {
        MenuScreens.ScreenConstructor<T, AbstractContainerScreen<T>> sc =
                (menu, inv, title) -> (AbstractContainerScreen<T>) p.factory().create(menu, inv, title);
        event.register(p.menuType(), sc);
    }
}
