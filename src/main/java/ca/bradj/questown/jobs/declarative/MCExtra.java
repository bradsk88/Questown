package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.interfaces.WorkStatusHandle;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;

public record MCExtra(
        TownInterface town,
        WorkStatusHandle<BlockPos, MCHeldItem> work,
        LivingEntity entity
) {
}
