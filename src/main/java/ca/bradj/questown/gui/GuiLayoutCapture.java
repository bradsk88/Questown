package ca.bradj.questown.gui;

import java.util.ArrayList;
import java.util.List;

/** Dev-only: records where wrapped text was drawn this frame, so {@link GuiLayoutLinter} can catch text running into other widgets. */
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

    public static void recordText(int x, int y, int width, int height, String label) {
        if (!active) {
            return;
        }
        BOXES.add(new GuiLayoutLinter.Box("text:'" + shorten(label) + "'", x, y, width, height));
    }

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
