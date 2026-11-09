package ca.bradj.questown.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuiRectTest {

    @Test
    void union_spansBothRects() {
        GuiRect previous = new GuiRect(10, 50, 13, 13);
        GuiRect next = new GuiRect(100, 50, 13, 13);
        assertEquals(new GuiRect(10, 50, 103, 13), GuiRect.union(previous, next));
    }

    @Test
    void union_ignoresAnEmptyRect() {
        GuiRect button = new GuiRect(10, 50, 13, 13);
        assertEquals(button, GuiRect.union(GuiRect.EMPTY, button));
        assertEquals(button, GuiRect.union(button, GuiRect.EMPTY));
    }

    @Test
    void centerArea_roundsTheOffset() {
        GuiRect outer = new GuiRect(10, 50, 103, 13);
        assertEquals(new GuiRect(47, 53, 30, 8), GuiRect.centerArea(outer, 30, 8));
    }

    @Test
    void contains_includesTheTopLeftEdgeOnly() {
        GuiRect bar = new GuiRect(10, 20, 5, 4);
        assertTrue(bar.contains(10, 20));
        assertTrue(bar.contains(14.9, 23.9));
        assertFalse(bar.contains(15, 20));
        assertFalse(bar.contains(10, 24));
    }

    @Test
    void moves_keepTheSize() {
        GuiRect r = new GuiRect(10, 20, 5, 4);
        assertEquals(new GuiRect(10, 17, 5, 4), r.moveUp(3));
        assertEquals(new GuiRect(10, 23, 5, 4), r.moveDown(3));
        assertEquals(new GuiRect(8, 18, 9, 8), r.expandBy(2));
    }
}
