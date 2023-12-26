package ca.bradj.questown.jobs;

import com.google.common.collect.ImmutableList;

public interface Snapshot<H extends HeldItem<H, ?>> {
    String statusStringValue();

    String jobStringValue();

    ImmutableList<H> items();

    JobID jobId();
}
