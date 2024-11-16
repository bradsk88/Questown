package ca.bradj.questown.jobs.special;

import ca.bradj.questown.integration.jobs.BeforeInitEvent;
import ca.bradj.questown.integration.jobs.JobPhaseModifier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public class RequireTwoFreeSpotsSpecialRule extends
        JobPhaseModifier {

    public RequireTwoFreeSpotsSpecialRule() {

    }

    @Override
    public void beforeInit(BeforeInitEvent bxEvent) {
        super.beforeInit(bxEvent);
        bxEvent.jobBlockCheckReplacer().accept(before -> (heldItems, bs, block) -> {
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
            if (hasTwoFreeSlots(handler.getSlots(), handler::getStackInSlot)) {
                return before.test(heldItems, bs, block);
            }
            return false;
        });
        bxEvent.supplyRoomCheckReplacer().accept(before -> (heldItems, room) -> {
            @Nullable BlockPos jBlock = WorkSpotFromHeldItemSpecialRule
                    .getJobBlockPositionFromHeldItems(heldItems); // TODO: If holding stock request, skip this rule
            for (Map.Entry<BlockPos, Block> b : room.getContainedBlocks().entrySet()) {
                if (jBlock != null && !jBlock.equals(b.getKey())) {
                    return before.test(heldItems, room);
                }
                if (!(b.getValue() instanceof ChestBlock cb)) {
                    continue;
                }
                ServerLevel serverLevel = bxEvent.level().get();
                Container cont = ChestBlock.getContainer(
                        cb,
                        serverLevel.getBlockState(b.getKey()),
                        serverLevel,
                        b.getKey(),
                        true
                );
                if (cont == null) {
                    continue;
                }
                if (hasTwoFreeSlots(cont.getContainerSize(), cont::getItem)) {
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
