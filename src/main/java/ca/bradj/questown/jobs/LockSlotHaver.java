package ca.bradj.questown.jobs;

import com.google.common.collect.ImmutableList;

public interface LockSlotHaver {
    ImmutableList<Boolean> getSlotLockStatuses();

    void lockSlot(int slotIndex);

    void unlockSlot(int slotIndex);
}
