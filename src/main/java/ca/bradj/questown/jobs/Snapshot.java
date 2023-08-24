package ca.bradj.questown.jobs;

import com.google.common.collect.ImmutableList;

public interface Snapshot<H extends HeldItem<H, ?>> {
    String statusStringValue();

    ImmutableList<H> items();
}
