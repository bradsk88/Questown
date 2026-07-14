package ca.bradj.questown.gui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Layer-2 GUI layout linter — pure logic, no Minecraft client needed. Proves the invariant
 * checks flag the bug classes surfaced in the 2026-07-14 flag-menu play-test.
 */
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
        // 70px label in a 60px button (available 54) must be flagged.
        List<GuiLayoutLinter.Box> boxes = List.of(
                new GuiLayoutLinter.Box("btn", 10, 10, 60, 20, 70));
        List<GuiLayoutLinter.Violation> v = GuiLayoutLinter.check(320, 240, boxes);
        assertEquals(1, v.size());
        assertEquals("label-overflow", v.get(0).kind());
    }

    @Test
    void labelExactlyFits_notFlagged() {
        // 54px label in a 60px button (available 54) is the boundary — must pass.
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

    /**
     * Layer 1 detection over a faithful reconstruction of FlagCraftingScreen's text/widget
     * geometry. The recipe descriptions are drawn with a 56px wrap width (backgroundWidth-120),
     * so they wrap to several lines and overrun their rows; the "begin moving" description
     * overruns the button below it. Capturing those text bboxes and running the linter must flag
     * the overflow — the crafting-tab bug the play-tester reported.
     */
    @Test
    void craftingTabTextOverflow_flaggedAsOverlap() {
        int bgX = 125; // (427 - 176) / 2
        int bgY = 37;  // (240 - 166) / 2
        int line = 13; // ~ font.lineHeight * 1.5
        List<GuiLayoutLinter.Box> boxes = List.of(
                // Recipe-row descriptions: 56px wrap → ~4 lines tall, so row 1 spills into row 2.
                new GuiLayoutLinter.Box("text:row1-desc", bgX + 12, bgY + 50, 56, 4 * line),
                new GuiLayoutLinter.Box("text:row2-desc", bgX + 12, bgY + 90, 56, 4 * line),
                // "Begin moving" description spilling into its button at bgY+128.
                new GuiLayoutLinter.Box("text:begin-desc", bgX + 12, bgY + 110, 152, 3 * line),
                new GuiLayoutLinter.Box("widget:begin-moving", bgX + 12, bgY + 128, 152, 20));
        List<GuiLayoutLinter.Violation> v = GuiLayoutLinter.check(427, 240, boxes);
        assertTrue(v.stream().anyMatch(x -> x.kind().equals("overlap")),
                "expected the overflowing crafting-tab text to be flagged; got " + v);
    }
}
