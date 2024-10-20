package ca.bradj.questown.gui;

import ca.bradj.questown.mc.JEI;
import com.google.common.collect.ImmutableList;
import mezz.jei.api.gui.drawable.IDrawableStatic;

import java.util.function.Consumer;

public class Tabs {
    private final ImmutableList<Tab> tabs;
    private final IDrawableStatic tab;
    private final IDrawableStatic unTab;

    private final int X_OFFSET = 6;
    private final int Y_OFFSET = 3;

    public Tabs(ImmutableList<Tab> tabs) {
        this.tabs = tabs;
        this.tab = JEI.getTabSelected();
        this.unTab = JEI.getTabUnselected();
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

    public void draw(RenderContext rc, int bgX, int bgY) {
        for (int i = 0; i < tabs.size(); i++) {
            IDrawableStatic tab = unTab;
            if (tabs.get(i).selected()) {
                tab = this.tab;
            }
            int tabX = bgX + (tab.getWidth() * i);
            int tabY = bgY - tab.getHeight();
            tab.draw(rc.stack(), tabX + X_OFFSET, tabY + Y_OFFSET);
            tabs.get(i).renderFunc().accept(rc, tabX, tabY);
        }
    }

    public void mouseClicked(int bgX, int bgY, double mouseX, double mouseY) {
        int tabsY = bgY - this.unTab.getHeight() + Y_OFFSET;
        for (int i = 0; i < tabs.size(); i++) {
            int tabX = bgX + (unTab.getWidth() * i) + X_OFFSET;
            if (mouseX > tabX && mouseX < tabX + tab.getWidth() && mouseY > tabsY && mouseY < tabsY + unTab.getHeight()) {
                tabs.get(i).onClick().run();
                return;
            }
        }

    }
}
