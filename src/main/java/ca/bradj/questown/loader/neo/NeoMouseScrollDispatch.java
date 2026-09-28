package ca.bradj.questown.loader.neo;

import ca.bradj.questown.gui.ScrollableScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.InputEvent;

/**
 * Client binder: forwards vanilla mouse-wheel input to the current screen when it is a
 * {@link ScrollableScreen} (vanilla dropped the 1.19-style {@code Screen#mouseScrolled(x, y, delta)}).
 */
public class NeoMouseScrollDispatch {
    @SubscribeEvent
    public void onMouseScrolling(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof ScrollableScreen scrollable
                && scrollable.mouseScrolled(event.getScrollDeltaX(), event.getScrollDeltaY(), event.getScrollDeltaY())) {
            event.setCanceled(true);
        }
    }
}
