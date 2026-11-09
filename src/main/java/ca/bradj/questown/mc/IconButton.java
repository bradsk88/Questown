package ca.bradj.questown.mc;

import ca.bradj.questown.gui.GuiArt;
import ca.bradj.questown.gui.GuiRect;
import ca.bradj.questown.gui.GuiSprite;
import ca.bradj.questown.gui.RenderContext;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.CommonComponents;

/**
 * A small button showing an icon instead of a label.
 */
public class IconButton extends Button {
    private final GuiSprite icon;

    public IconButton(
            int x,
            int y,
            int width,
            int height,
            GuiSprite icon,
            Button.OnPress onPress
    ) {
        super(x, y, width, height, CommonComponents.EMPTY, onPress, Button.DEFAULT_NARRATION);
        this.icon = icon;
    }

    public GuiRect getArea() {
        return new GuiRect(getX(), getY(), getWidth(), getHeight());
    }

    @Override
    protected void renderWidget(
            GuiGraphics gui,
            int mouseX,
            int mouseY,
            float partialTicks
    ) {
        RenderContext ctx = new RenderContext(Minecraft.getInstance().getItemRenderer(), gui);
        boolean hovered = isMouseOver(mouseX, mouseY);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        GuiArt.button(active, hovered).draw(ctx, getX(), getY(), getWidth(), getHeight());

        int color = !active ? 0xFFA0A0A0 : hovered ? 0xFFFFFFFF : 0xFFE0E0E0;
        RenderSystem.setShaderColor(
                (color >> 16 & 255) / 255.0F,
                (color >> 8 & 255) / 255.0F,
                (color & 255) / 255.0F,
                1.0F
        );
        PoseStack pose = gui.pose();
        pose.pushPose();
        pose.translate(getX() + (getWidth() - icon.getWidth()) / 2.0, getY() + (getHeight() - icon.getHeight()) / 2.0, 0);
        icon.draw(ctx, 0, 0);
        pose.popPose();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
