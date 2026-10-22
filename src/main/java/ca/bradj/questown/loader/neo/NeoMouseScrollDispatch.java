package ca.bradj.questown.loader.neo;

import ca.bradj.questown.gui.ScrollableScreen;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Client binder: forwards mouse-wheel input on an open screen to it when it is a
 * {@link ScrollableScreen} (vanilla dropped the 1.19-style {@code Screen#mouseScrolled(x, y, delta)}).
 *
 * <p>Listens to {@link ScreenEvent.MouseScrolled.Pre}, not {@code InputEvent.MouseScrollingEvent}:
 * NeoForge posts the input event only when no screen is open ({@code MouseHandler.onScroll}'s
 * no-screen branch), so a dispatch on it never reached a Questown menu and paged screens stopped
 * scrolling between pages (2026-10-11 playtest, verified in the 1.20.6 bytecode 2026-10-22).
 */
public class NeoMouseScrollDispatch {
    @SubscribeEvent
    public void onScreenMouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
        if (event.getScreen() instanceof ScrollableScreen scrollable
                && scrollable.mouseScrolled(event.getMouseX(), event.getMouseY(), event.getScrollDeltaY())) {
            event.setCanceled(true);
        }
    }
}
