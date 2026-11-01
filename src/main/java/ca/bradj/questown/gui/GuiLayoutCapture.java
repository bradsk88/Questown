package ca.bradj.questown.gui;

import java.util.ArrayList;
import java.util.List;

/**
 * Layer 1 of the GUI layout-validation mechanism: a dev-only per-frame buffer of wrapped-text
 * bounding boxes captured at the draw choke point ({@code Compat.drawDarkTextWrap}). The client
 * render hook activates capture around a screen's render, then feeds the collected text boxes —
 * together with the widget boxes — into {@link GuiLayoutLinter}, so a text block that overflows
 * its row and bleeds into the next element (the crafting-tab bug) is caught as an overlap.
 *
 * <p>Holds no client-only types, so it is safe to reference from the shared {@code Compat} layer;
 * it stays inert on a server because only the client hook ever calls {@link #begin()}.
 */
public final class GuiLayoutCapture {

    private static boolean active = false;
    private static final List<GuiLayoutLinter.Box> BOXES = new ArrayList<>();

    private GuiLayoutCapture() {
    }

    public static void begin() {
        active = true;
        BOXES.clear();
    }

    public static boolean isActive() {
        return active;
    }

    /** Record one wrapped-text block's bounding box. No-op unless capture is active. */
    public static void recordText(int x, int y, int width, int height, String label) {
        if (!active) {
            return;
        }
        BOXES.add(new GuiLayoutLinter.Box("text:'" + shorten(label) + "'", x, y, width, height));
    }

    /** End capture and return the collected text boxes. */
    public static List<GuiLayoutLinter.Box> end() {
        active = false;
        List<GuiLayoutLinter.Box> out = new ArrayList<>(BOXES);
        BOXES.clear();
        return out;
    }

    private static String shorten(String s) {
        String t = s.replace('\n', ' ').trim();
        return t.length() <= 24 ? t : t.substring(0, 24) + "…";
    }
}
