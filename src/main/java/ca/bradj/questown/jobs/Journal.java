package ca.bradj.questown.jobs;

import com.google.common.collect.ImmutableList;

public interface Journal<STATUS, I, SNAPSHOT> extends ItemsHolder<I> {

    STATUS getStatus();

    void addStatusListener(StatusListener o);

    void initializeStatus(STATUS s);

    ImmutableList<Boolean> getSlotLockStatuses();

    SNAPSHOT getSnapshot();

    void initialize(SNAPSHOT journal);
}
