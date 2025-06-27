package ca.bradj.questown.integration.jobs;

import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.jobs.WorkLocation;
import net.minecraft.core.BlockPos;

import java.util.Collection;

public interface JobCheck {

    boolean test(
            Collection<MCHeldItem> heldItems,
            WorkLocation.BlockInfo blockState,
            BlockPos block
    );
}
