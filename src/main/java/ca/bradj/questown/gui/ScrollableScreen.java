package ca.bradj.questown.gui;

/** A screen that wants mouse-scroll events. */
public interface ScrollableScreen {
    boolean mouseScrolled(double scrollX, double scrollY, double scrollDelta);
}
