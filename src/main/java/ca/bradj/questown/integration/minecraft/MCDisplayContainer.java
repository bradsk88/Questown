package ca.bradj.questown.integration.minecraft;

/** A display as town storage. It takes no general deposits, and display jobs never take items from one. */
public class MCDisplayContainer extends MCContainer {

    public MCDisplayContainer(DisplaySlots slots) {
        super(slots);
    }

    @Override
    public boolean canAcceptIfSpaceAllows(MCTownItem item) {
        return false;
    }
}
