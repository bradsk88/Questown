package ca.bradj.questown.loader.forge;

import ca.bradj.questown.gui.ScrollableScreen;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.client.event.ScreenEvent;

/**
 * Client binder: forwards mouse-wheel input on an open screen to it when it is a
 * {@link ScrollableScreen}. Listens to {@link ScreenEvent.MouseScrolled.Pre}: the input event
 * ({@code InputEvent.MouseScrollingEvent}) only fires when no screen is open.
 */
public class ForgeMouseScrollDispatch {
    @SubscribeEvent
    public void onScreenMouseScrolled_qfmsd17(ScreenEvent.MouseScrolled.Pre event) {
        if (event.getScreen() instanceof ScrollableScreen scrollable
                && scrollable.mouseScrolled(event.getMouseX(), event.getMouseY(), event.getScrollDelta())) {
            event.setCanceled(true);
        }
    }
}
