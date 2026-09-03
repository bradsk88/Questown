package ca.bradj.questown.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.entity.ItemRenderer;

public record RenderContext(
        ItemRenderer itemRenderer,
        GuiGraphics gui
) {
    // The 1.20.1 render pipeline carries a GuiGraphics; a PoseStack is still needed by the
    // 3D/transform blit helpers, so it is derived on demand from the GuiGraphics.
    public PoseStack stack() {
        return gui.pose();
    }
}
