package ca.bradj.questown.loader.forge;

import ca.bradj.questown.gui.ScrollableScreen;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.client.event.ScreenEvent;

/** Sends mouse-wheel input on an open screen to it, when it is a {@link ScrollableScreen}. */
public class ForgeMouseScrollDispatch {
    @SubscribeEvent
    public void onScreenMouseScrolled_qfmsd17(ScreenEvent.MouseScrolled.Pre event) {
        if (event.getScreen() instanceof ScrollableScreen scrollable
                && scrollable.mouseScrolled(event.getMouseX(), event.getMouseY(), event.getScrollDelta())) {
            event.setCanceled(true);
        }
    }
}
