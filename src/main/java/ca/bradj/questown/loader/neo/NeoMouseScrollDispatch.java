package ca.bradj.questown.loader.neo;

import ca.bradj.questown.gui.ScrollableScreen;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** Sends mouse-wheel input on an open screen to it, when it is a {@link ScrollableScreen}. The input event only fires with no screen open, so this listens to the screen event. */
public class NeoMouseScrollDispatch {
    @SubscribeEvent
    public void onScreenMouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
        if (event.getScreen() instanceof ScrollableScreen scrollable
                && scrollable.mouseScrolled(event.getMouseX(), event.getMouseY(), event.getScrollDeltaY())) {
            event.setCanceled(true);
        }
    }
}
