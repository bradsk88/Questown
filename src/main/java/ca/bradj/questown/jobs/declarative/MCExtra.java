package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.town.entity.TownieRuntime;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.interfaces.WorkStatusHandle;
import net.minecraft.core.BlockPos;

public record MCExtra(
        TownInterface town,
        WorkStatusHandle<BlockPos, MCHeldItem> work,
        TownieRuntime townie
) {
}
