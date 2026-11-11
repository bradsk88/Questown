package ca.bradj.questown.loader.forge;

import ca.bradj.questown.loader.Loader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

/** The Forge 1.19.2 client island: menu screens, client-only handlers, the local player. */
public final class ForgeLoaderClient {

    private ForgeLoaderClient() {
    }

    /** Runs in the client-setup work queue, after QuestownClient.registerScreens filled the list. */
    public static void init() {
        ForgeLoader forge = (ForgeLoader) Loader.api();
        if (forge.pendingScreens().isEmpty()) {
            ca.bradj.questown.QuestownClient.registerScreens();
        }
        int count = forge.pendingScreens().size();
        for (ForgeLoader.PendingScreen<?> p : forge.pendingScreens()) {
            registerOne(p);
        }
        forge.pendingScreens().clear();
        ca.bradj.questown.QT.LOGGER.info("[questown] registered {} menu screens on the client", count);
    }

    @SuppressWarnings("unchecked")
    private static <T extends AbstractContainerMenu> void registerOne(ForgeLoader.PendingScreen<T> p) {
        MenuScreens.ScreenConstructor<T, AbstractContainerScreen<T>> sc =
                (menu, inv, title) -> (AbstractContainerScreen<T>) p.factory().create(menu, inv, title);
        MenuScreens.register(p.menuType(), sc);
    }

    public static Player localPlayer() {
        return Minecraft.getInstance().player;
    }
}
