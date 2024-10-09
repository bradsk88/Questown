package ca.bradj.questown.jobs.special;

import ca.bradj.questown.integration.jobs.BeforeInitEvent;
import ca.bradj.questown.integration.jobs.JobPhaseModifier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import java.util.Optional;
import java.util.function.Predicate;

public class RequireTwoFreeSpotsSpecialRule extends
        JobPhaseModifier {

    public RequireTwoFreeSpotsSpecialRule() {

    }

    @Override
    public void beforeInit(BeforeInitEvent bxEvent) {
        super.beforeInit(bxEvent);
        bxEvent.jobBlockCheckReplacer().accept(before -> (block) -> {
            BlockEntity entity = bxEvent.level().get().getBlockEntity(block);
            if (entity == null) {
                return false;
            }
            LazyOptional<IItemHandler> itemHandler = entity.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY);
            if (!itemHandler.isPresent()) {
                return false;
            }
            Optional<IItemHandler> resolve = itemHandler.resolve();
            if (resolve.isEmpty()) {
                return false;
            }
            IItemHandler handler = resolve.get();
            return hasTwoFreeSlots(handler, before, block);
        });
    }

    private static boolean hasTwoFreeSlots(
            IItemHandler handler,
            Predicate<BlockPos> before,
            BlockPos pos
    ) {
        int emptySpots = 0;
        for (int i = 0; i < handler.getSlots(); i++) {
            if (handler.getStackInSlot(i).isEmpty()) {
                emptySpots++;
            }
            if (emptySpots > 1) {
                return before.test(pos);
            }
        }
        return false;
    }
}
