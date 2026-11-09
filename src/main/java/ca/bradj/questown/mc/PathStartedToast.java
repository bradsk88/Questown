package ca.bradj.questown.mc;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** An advancement-style toast whose header reads "Path started" instead of "Goal Reached!". */
public class PathStartedToast implements Toast {

    public static final String HEADER_KEY = "toast.questown.path_started";
    private static final long DISPLAY_MS = 5000L;
    private static final ResourceLocation BACKGROUND = new ResourceLocation("toast/advancement");

    private final Component name;
    private final ItemStack icon;

    public PathStartedToast(Component name, ItemStack icon) {
        this.name = name;
        this.icon = icon;
    }

    @Override
    public Visibility render(GuiGraphics graphics, ToastComponent toasts, long timeSinceLastVisible) {
        graphics.blitSprite(BACKGROUND, 0, 0, this.width(), this.height());
        graphics.drawString(toasts.getMinecraft().font, Compat.translatable(HEADER_KEY), 30, 7, 0xFFFF00FF, false);
        graphics.drawString(toasts.getMinecraft().font, name, 30, 18, 0xFFFFFFFF, false);
        graphics.renderFakeItem(icon, 8, 8);
        return timeSinceLastVisible >= DISPLAY_MS ? Visibility.HIDE : Visibility.SHOW;
    }
}
