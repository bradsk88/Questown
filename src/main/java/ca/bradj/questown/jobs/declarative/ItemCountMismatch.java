package ca.bradj.questown.jobs.declarative;

public class ItemCountMismatch extends Exception{

    public ItemCountMismatch(int expectedSize) {
        super(String.format(
                "Expected %s items. They can be empty.",
                expectedSize
        ));
    }
}
