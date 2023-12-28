package ca.bradj.questown.jobs;

public interface ImmutableSnapshot<H extends HeldItem<H, ?>, SELF> extends Snapshot<H> {
    SELF withSetItem(int itemIndex, H item);
}
