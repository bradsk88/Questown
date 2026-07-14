package ca.bradj.questown.gui;

import java.util.ArrayList;
import java.util.List;

/**
 * Layer 2 of the GUI layout-validation mechanism: a pure, client-free checker for widget
 * layout invariants. It operates on plain {@link Box} rectangles so it is unit-testable
 * without a Minecraft client; a client-side adapter ({@code GuiLayoutCheckClientEvents})
 * extracts the boxes from a real {@code Screen} and feeds real {@code Font} metrics in.
 *
 * <p>Checks, per the found play-test bugs:
 * <ul>
 *   <li><b>off-screen</b> — a widget extends past the screen edges (overflow at small sizes)</li>
 *   <li><b>label-overflow</b> — a button's text is wider than the button can show</li>
 *   <li><b>overlap</b> — two interactive widgets occupy the same pixels</li>
 * </ul>
 * Render-time wrapped-text overflow (e.g. {@code drawDarkTextWrap} descriptions bleeding
 * into the next row) is a separate concern handled by the Layer 1 render-capture hook.
 */
public final class GuiLayoutLinter {

    /** Slack subtracted from a button's width before checking its label fits (vanilla-ish padding). */
    public static final int BUTTON_LABEL_PADDING = 6;

    private GuiLayoutLinter() {
    }

    /**
     * A rectangular element to validate.
     *
     * @param contentWidth rendered pixel width of the element's fit-constrained label, or
     *                     {@code -1} when the element has no such label.
     */
    public record Box(String id, int x, int y, int width, int height, int contentWidth) {
        public Box(String id, int x, int y, int width, int height) {
            this(id, x, y, width, height, -1);
        }
    }

    public record Violation(String id, String kind, String detail) {
        @Override
        public String toString() {
            return id + ": " + kind + " — " + detail;
        }
    }

    public static List<Violation> check(int screenWidth, int screenHeight, List<Box> boxes) {
        List<Violation> out = new ArrayList<>();
        for (Box b : boxes) {
            checkBounds(b, screenWidth, screenHeight, out);
            checkLabelFit(b, out);
        }
        checkOverlaps(boxes, out);
        return out;
    }

    private static void checkBounds(Box b, int screenWidth, int screenHeight, List<Violation> out) {
        if (b.x() >= 0 && b.y() >= 0 && b.x() + b.width() <= screenWidth && b.y() + b.height() <= screenHeight) {
            return;
        }
        out.add(new Violation(b.id(), "off-screen",
                "box [" + b.x() + "," + b.y() + " " + b.width() + "x" + b.height()
                        + "] exceeds screen " + screenWidth + "x" + screenHeight));
    }

    private static void checkLabelFit(Box b, List<Violation> out) {
        if (b.contentWidth() < 0) {
            return;
        }
        int available = b.width() - BUTTON_LABEL_PADDING;
        if (b.contentWidth() <= available) {
            return;
        }
        out.add(new Violation(b.id(), "label-overflow",
                "label width " + b.contentWidth() + " > available " + available
                        + " (button width " + b.width() + ")"));
    }

    private static void checkOverlaps(List<Box> boxes, List<Violation> out) {
        for (int i = 0; i < boxes.size(); i++) {
            for (int j = i + 1; j < boxes.size(); j++) {
                Box a = boxes.get(i);
                Box b = boxes.get(j);
                if (intersects(a, b)) {
                    out.add(new Violation(a.id(), "overlap", "overlaps " + b.id()));
                }
            }
        }
    }

    private static boolean intersects(Box a, Box b) {
        return a.x() < b.x() + b.width()
                && a.x() + a.width() > b.x()
                && a.y() < b.y() + b.height()
                && a.y() + a.height() > b.y();
    }
}
