package ca.bradj.questown.gui;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.vertex.PoseStack;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.common.Internal;
import mezz.jei.common.gui.textures.Textures;
import net.minecraft.client.Minecraft;

import java.util.function.Consumer;

public class Tabs {
    private final ImmutableList<Tab> tabs;
    private final IDrawableStatic tab;
    private final IDrawableStatic unTab;

    public Tabs(ImmutableList<Tab> tabs) {
        this.tabs = tabs;
        Textures textures = Internal.getTextures();
        this.tab = textures.getTabSelected();
        this.unTab = textures.getTabUnselected();
    }

    public boolean renderTooltip(
            int bgX, int bgY, int mouseX, int mouseY, Consumer<String> renderFn
    ) {
        int tabsY = bgY - this.unTab.getHeight();
        for (int i = 0; i < tabs.size(); i++) {
            int tabX = bgX + (unTab.getWidth() * i);
            if (mouseX > tabX && mouseX < tabX + tab.getWidth() && mouseY > tabsY && mouseY < tabsY + unTab.getHeight()) {
                renderFn.accept(tabs.get(i).titleKey());
                return true;
            }
        }
        return false;
    }

    public void draw(PoseStack stack, int bgX, int bgY) {
        for (int i = 0; i < tabs.size(); i++) {
            IDrawableStatic tab = unTab;
            if (tabs.get(i).selected()) {
                tab = this.tab;
            }
            int tabX = bgX + (tab.getWidth() * i);
            int tabY = bgY - tab.getHeight();
            tab.draw(stack, tabX + 6, tabY + 3);
            tabs.get(i).renderFunc().accept(stack, tabX, tabY);
        }
    }

    public void mouseClicked(int bgX, int bgY, double mouseX, double mouseY) {
        int tabsY = bgY - this.unTab.getHeight();
        for (int i = 0; i < tabs.size(); i++) {
            int tabX = bgX + (unTab.getWidth() * i);
            if (mouseX > tabX && mouseX < tabX + tab.getWidth() && mouseY > tabsY && mouseY < tabsY + unTab.getHeight()) {
                tabs.get(i).onClick().run();
                return;
            }
        }

    }
}
