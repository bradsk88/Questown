package ca.bradj.questown.loader.neo;

import ca.bradj.questown.loader.Loader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;

/**
 * Client-only island for the NeoForge loader: the local-player send hook behind
 * {@code Channel.sendToServer}, the menu-screen replay at
 * {@link RegisterMenuScreensEvent}, and the client-only common-bus handlers.
 *
 * <p>Loaded only by the {@code questown.client} mod entry. A dedicated server never touches
 * this class, which keeps client class references (Minecraft, screens) out of the
 * constant pools of every class it is loaded on.
 */
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

    /**
     * Replays the neutral screen captures taken via {@code Loader.registerScreen} into
     * NeoForge's menu-screen registry, adapting the neutral factory to the vanilla
     * {@code ScreenConstructor} bound.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        NeoLoader neo = (NeoLoader) Loader.api();
        // This event fires before the FMLClientSetupEvent work queue runs, so the capture that
        // sits in that queue is still pending. Capture here (guarded, so the later queue call is
        // a no-op) instead of replaying an empty list and silently dropping every menu screen.
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
