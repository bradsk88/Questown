package ca.bradj.questown.gui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The layout linter catches overlapping and off-panel widgets. */
class GuiLayoutLinterTest {

    @Test
    void cleanLayout_producesNoViolations() {
        List<GuiLayoutLinter.Box> boxes = List.of(
                new GuiLayoutLinter.Box("craft-a", 100, 30, 60, 20, 40),
                new GuiLayoutLinter.Box("craft-b", 100, 70, 60, 20, 40),
                new GuiLayoutLinter.Box("begin-moving", 12, 128, 152, 20, 60));
        assertTrue(GuiLayoutLinter.check(320, 240, boxes).isEmpty());
    }

    @Test
    void widgetPastScreenEdge_flaggedOffScreen() {
        List<GuiLayoutLinter.Box> boxes = List.of(
                new GuiLayoutLinter.Box("too-wide", 290, 10, 60, 20));
        List<GuiLayoutLinter.Violation> v = GuiLayoutLinter.check(320, 240, boxes);
        assertEquals(1, v.size());
        assertEquals("off-screen", v.get(0).kind());
    }

    @Test
    void labelWiderThanButton_flaggedLabelOverflow() {
        List<GuiLayoutLinter.Box> boxes = List.of(
                new GuiLayoutLinter.Box("btn", 10, 10, 60, 20, 70));
        List<GuiLayoutLinter.Violation> v = GuiLayoutLinter.check(320, 240, boxes);
        assertEquals(1, v.size());
        assertEquals("label-overflow", v.get(0).kind());
    }

    @Test
    void labelExactlyFits_notFlagged() {
        // At the limit, it passes.
        List<GuiLayoutLinter.Box> boxes = List.of(
                new GuiLayoutLinter.Box("btn", 10, 10, 60, 20, 54));
        assertTrue(GuiLayoutLinter.check(320, 240, boxes).isEmpty());
    }

    @Test
    void overlappingWidgets_flaggedOverlap() {
        List<GuiLayoutLinter.Box> boxes = List.of(
                new GuiLayoutLinter.Box("a", 10, 10, 60, 20),
                new GuiLayoutLinter.Box("b", 40, 15, 60, 20));
        List<GuiLayoutLinter.Violation> v = GuiLayoutLinter.check(320, 240, boxes);
        assertTrue(v.stream().anyMatch(x -> x.kind().equals("overlap")));
    }

    /** Text drawn wider than its row on the flag crafting screen is caught as an overlap. */
    @Test
    void craftingTabTextOverflow_flaggedAsOverlap() {
        int bgX = 125;
        int bgY = 37;
        int line = 13;
        List<GuiLayoutLinter.Box> boxes = List.of(
                new GuiLayoutLinter.Box("text:row1-desc", bgX + 12, bgY + 50, 56, 4 * line),
                new GuiLayoutLinter.Box("text:row2-desc", bgX + 12, bgY + 90, 56, 4 * line),
                new GuiLayoutLinter.Box("text:begin-desc", bgX + 12, bgY + 110, 152, 3 * line),
                new GuiLayoutLinter.Box("widget:begin-moving", bgX + 12, bgY + 128, 152, 20));
        List<GuiLayoutLinter.Violation> v = GuiLayoutLinter.check(427, 240, boxes);
        assertTrue(v.stream().anyMatch(x -> x.kind().equals("overlap")),
                "expected the overflowing crafting-tab text to be flagged; got " + v);
    }
}
