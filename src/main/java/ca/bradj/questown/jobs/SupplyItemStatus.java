package ca.bradj.questown.jobs;

public enum SupplyItemStatus {
    NEEDS_ITEM,
    HAS_ITEM,
    NOT_REQUIRED;

    public boolean has() {
        return this != NEEDS_ITEM;
    }
}
