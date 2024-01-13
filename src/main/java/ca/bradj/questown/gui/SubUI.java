package ca.bradj.questown.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;

import java.util.function.Consumer;

public interface SubUI {
    void draw(PoseStack poseStack, int bgX, int bgY);

    void mouseClicked(int bgX, int bgY, double x, double y);

    boolean renderTooltip(int bgX, int bgY, int mouseX, int mouseY, Consumer<String> o);

    class Empty implements SubUI {

        @Override
        public void draw(PoseStack poseStack, int bgX, int bgY) {
        }

        @Override
        public void mouseClicked(int bgX, int bgY, double x, double y) {
        }

        @Override
        public boolean renderTooltip(int bgX, int bgY, int mouseX, int mouseY, Consumer<String> o) {
            return false;
        }
    }
}
