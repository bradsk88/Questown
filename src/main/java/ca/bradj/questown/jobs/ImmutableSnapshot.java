package ca.bradj.questown.jobs;

import com.google.common.collect.ImmutableList;

public interface ImmutableSnapshot<H extends HeldItem<H, ?>, SELF> extends Snapshot<H> {
    SELF withSetItem(int itemIndex, H item);

    SELF withItems(ImmutableList<H> items);
}
