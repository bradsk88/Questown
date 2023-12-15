package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.integration.minecraft.MCCoupledHeldItem;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.interfaces.WorkStatusHandle;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;

public record MCExtra(
        TownInterface town,
        WorkStatusHandle<BlockPos, MCCoupledHeldItem> work,
        LivingEntity entity
) {
}
