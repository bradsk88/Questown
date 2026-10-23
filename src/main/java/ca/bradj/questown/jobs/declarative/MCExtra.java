package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.town.entity.TownieRuntime;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.interfaces.WorkStatusHandle;
import net.minecraft.core.BlockPos;

public record MCExtra(
        WorkStatusHandle<BlockPos, MCHeldItem> work,
        TownieRuntime townie
) {
    /** The townie's town. */
    public TownInterface town() {
        return townie.getTown();
    }
}
