package ca.bradj.questown.jobs.leaver;

import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.jobs.Jobs;
import com.google.common.collect.ImmutableList;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerListener;
import net.minecraft.world.item.ItemStack;

public class LeaverContainerListener implements ContainerListener {
    private final LeaverJob job;

    public LeaverContainerListener(LeaverJob leaverJob) {
        job = leaverJob;
    }

    @Override
    public void containerChanged(Container p_18983_) {
        if (Jobs.isUnchanged(p_18983_, job.journal.getItems())) {
            return;
        }

        ImmutableList.Builder<MCHeldItem> b = ImmutableList.builder();

        for (int i = 0; i < p_18983_.getContainerSize(); i++) {
            ItemStack item = p_18983_.getItem(i);
            MCHeldItem element = MCHeldItem.fromMCItemStack(item);
            if (job.getSlotLockStatuses().get(i)) {
                element = element.locked();
            }
            b.add(element);
        }
        job.journal.setItemsNoUpdateNoCheck(b.build());
    }
}
