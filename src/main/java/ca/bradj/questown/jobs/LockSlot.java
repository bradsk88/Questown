package ca.bradj.questown.jobs;

import net.minecraft.world.inventory.DataSlot;

public class LockSlot extends DataSlot {

    private final int slotIndex;

    private final LockSlotHaver job;

    public LockSlot(
            int slotIndex,
            LockSlotHaver job
    ) {
        this.slotIndex = slotIndex;
        this.job = job;
    }

    @Override
    public int get() {
        return job.getSlotLockStatuses().get(slotIndex) ? 1 : 0;
    }

    @Override
    public void set(int p_39402_) {
        if (p_39402_ == 1) {
            job.lockSlot(slotIndex);
        } else {
            job.unlockSlot(slotIndex);
        }
    }

}
