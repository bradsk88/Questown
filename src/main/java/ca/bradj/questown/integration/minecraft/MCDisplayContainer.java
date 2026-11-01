package ca.bradj.questown.integration.minecraft;

/**
 * Town storage that is a display (an armor stand or item frame, {@link DisplaySlots}). It counts
 * as storage, but takes no general deposits, and a display job never sources its item from one
 * (it would move armor from stand to stand).
 */
public class MCDisplayContainer extends MCContainer {

    public MCDisplayContainer(DisplaySlots slots) {
        super(slots);
    }

    @Override
    public boolean canAcceptIfSpaceAllows(MCTownItem item) {
        return false;
    }
}
