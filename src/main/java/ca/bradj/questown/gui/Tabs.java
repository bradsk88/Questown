package ca.bradj.questown.gui;

import ca.bradj.questown.core.UtilClean;
import com.google.common.collect.ImmutableList;

import java.util.function.Consumer;

public class Tabs {
    private final ImmutableList<Tab> tabs;
    private final GuiSprite tab;
    private final GuiSprite unTab;

    private final int X_OFFSET = 6;
    private final int Y_OFFSET = 3;

    public Tabs(ImmutableList<Tab> tabs) {
        this.tabs = tabs;
        this.tab = GuiArt.getTabSelected();
        this.unTab = GuiArt.getTabUnselected();
    }

    public boolean renderTooltip(
            int bgX,
            int bgY,
            int mouseX,
            int mouseY,
            Consumer<String> renderFn
    ) {
        int tabTopY = bgY - this.unTab.getHeight();
        for (int i = 0; i < tabs.size(); i++) {
            int tabLeftX = bgX + (unTab.getWidth() * i);
            if (UtilClean.isCoordInBox(mouseX, mouseY, tabLeftX, tabTopY, tab.getWidth(), unTab.getHeight())) {
                renderFn.accept(tabs.get(i).titleKey());
                return true;
            }
        }
        return false;
    }

    public void draw(
            RenderContext rc,
            int bgX,
            int bgY
    ) {
        for (int i = 0; i < tabs.size(); i++) {
            GuiSprite tab = unTab;
            if (tabs.get(i).selected()) {
                tab = this.tab;
            }
            int tabX = bgX + (tab.getWidth() * i);
            int tabY = bgY - tab.getHeight();
            tab.draw(rc, tabX + X_OFFSET, tabY + Y_OFFSET);
            tabs.get(i).renderFunc().accept(rc, tabX, tabY);
            if (tabs.get(i).hasNotification()) {
                rc.fill(tabX + tab.getWidth() - 2, tabY + Y_OFFSET + 2, tabX + tab.getWidth() + 2, tabY + Y_OFFSET + 6, 0xFFFF3333);
            }
        }
    }

    public boolean mouseClicked(
            int bgX,
            int bgY,
            double mouseX,
            double mouseY
    ) {
        int tabsY = bgY - this.unTab.getHeight() + Y_OFFSET;
        for (int i = 0; i < tabs.size(); i++) {
            int tabLeftX = bgX + (unTab.getWidth() * i) + X_OFFSET;
            if (UtilClean.isCoordInBox(mouseX, mouseY, tabLeftX, tabsY, tab.getWidth(), unTab.getHeight())) {
                tabs.get(i).onClick().run();
                return true;
            }
        }
        return false;

    }

    public boolean hasTab(String titleKey) {
        return tabs.stream().anyMatch(t -> t.titleKey().equals(titleKey));
    }

    // Opens the tab with this title. False when there is no such tab.
    public boolean openTab(String titleKey) {
        for (Tab t : tabs) {
            if (t.titleKey().equals(titleKey)) {
                t.onClick().run();
                return true;
            }
        }
        return false;
    }
}
