package ca.bradj.questown.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.ItemRenderer;

public record RenderContext(
        ItemRenderer itemRenderer,
        PoseStack stack
) {
}
