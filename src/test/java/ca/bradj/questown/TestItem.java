package ca.bradj.questown;

import ca.bradj.questown.jobs.Item;

public record TestItem(
        String value
) implements Item<TestItem> {

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public boolean isFood() {
        return false;
    }

    @Override
    public TestItem shrink() {
        return null;
    }

    @Override
    public String getShortName() {
        return "";
    }

    @Override
    public TestItem unit() {
        return null;
    }

    @Override
    public int quantity() {
        return 0;
    }
}
