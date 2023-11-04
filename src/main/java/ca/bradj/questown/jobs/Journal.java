package ca.bradj.questown.jobs;

import com.google.common.collect.ImmutableList;

import java.util.function.Function;

public interface Journal<STATUS, I, SNAPSHOT> extends ItemsHolder<I> {

    STATUS getStatus();

    Function<Void, Void> addStatusListener(StatusListener o);

    void initializeStatus(STATUS s);

    ImmutableList<Boolean> getSlotLockStatuses();

    SNAPSHOT getSnapshot();

    void initialize(SNAPSHOT journal);

    void setItem(
            int idx,
            I mcHeldItem
    );

    void removeStatusListener(StatusListener o);
}
