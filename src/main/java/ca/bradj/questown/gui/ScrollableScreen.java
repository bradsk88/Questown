package ca.bradj.questown.gui;

/**
 * Neutral mouse-scroll entry point for Questown screens. Vanilla removed the
 * {@code Screen#mouseScrolled(double, double, double)} hook in 1.20.6, so the active loader
 * (see {@code loader.neo.NeoMouseScrollDispatch}) watches the client input event and forwards it
 * to the current screen when this is implemented.
 */
public interface ScrollableScreen {
    boolean mouseScrolled(double scrollX, double scrollY, double scrollDelta);
}
