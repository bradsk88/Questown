package ca.bradj.questown.jobs.special;

import ca.bradj.questown.integration.jobs.BeforeInitEvent;
import ca.bradj.questown.integration.jobs.JobPhaseModifier;
import ca.bradj.questown.world.QTWorldAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.function.Function;

public class RequireTwoFreeSpotsSpecialRule extends
        JobPhaseModifier {

    public RequireTwoFreeSpotsSpecialRule() {

    }

    @Override
    public void beforeInit(BeforeInitEvent bxEvent) {
        super.beforeInit(bxEvent);
        bxEvent.jobBlockCheckReplacer().accept(before -> (ctx) -> {
            QTWorldAccess world = bxEvent.world().get();
            if (!world.isContainer(ctx.blockPos())) {
                return false;
            }
            int slots = world.getContainerSlotCount(ctx.blockPos());
            if (hasTwoFreeSlots(slots, i -> world.getContainerSlot(ctx.blockPos(), i))) {
                return before.test(ctx);
            }
            return false;
        });
        bxEvent.supplyRoomCheckReplacer().accept(before -> (heldItems, room) -> {
            @Nullable BlockPos jBlock = WorkSpotFromHeldItemSpecialRule
                    .getJobBlockPositionFromHeldItems(heldItems);
            for (Map.Entry<BlockPos, Block> b : room.getContainedBlocks().entrySet()) {
                if (jBlock != null && !jBlock.equals(b.getKey())) {
                    return before.test(heldItems, room);
                }
                QTWorldAccess world = bxEvent.world().get();
                if (!world.isContainer(b.getKey())) {
                    continue;
                }
                int slots = world.getContainerSlotCount(b.getKey());
                if (hasTwoFreeSlots(slots, i -> world.getContainerSlot(b.getKey(), i))) {
                    return before.test(heldItems, room);
                }
            }
            return false;
        });
    }

    private static boolean hasTwoFreeSlots(
            int slots,
            Function<Integer, ItemStack> getStack
    ) {
        int emptySpots = 0;
        for (int i = 0; i < slots; i++) {
            if (getStack.apply(i).isEmpty()) {
                emptySpots++;
            }
            if (emptySpots > 1) {
                return true;
            }
        }
        return false;
    }
}
