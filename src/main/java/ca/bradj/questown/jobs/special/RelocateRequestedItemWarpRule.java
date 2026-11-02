package ca.bradj.questown.jobs.special;

import ca.bradj.questown.integration.jobs.JobPhaseModifier;
import ca.bradj.questown.integration.jobs.QTNativeRule;
import ca.bradj.questown.integration.jobs.WarpTickEvent;
import ca.bradj.questown.integration.minecraft.MCContainer;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.integration.minecraft.MCTownState;
import ca.bradj.questown.items.StockRequestItem;
import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.jobs.requests.WorkRequest;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** During a warp, moves a requested item from other chests into the chest that holds its stock request, as the fetcher would. Stops when the target is down to {@link #MIN_FREE_SLOTS_IN_TARGET} free slots. */
public class RelocateRequestedItemWarpRule extends JobPhaseModifier implements QTNativeRule {

    private static final int MIN_FREE_SLOTS_IN_TARGET = 2;
    private static final int MAX_RELOCATIONS_PER_REQUEST = 64;

    private record RequestTarget(BlockPos chestPos, WorkRequest request) {
    }

    @Override
    @SuppressWarnings("unchecked")
    public <X> X onWarpTick(X town, WarpTickEvent event) {
        if (!(town instanceof MCTownState mc)) {
            return town;
        }
        return (X) relocateAllUsableRequests(mc);
    }

    private static MCTownState relocateAllUsableRequests(MCTownState town) {
        for (RequestTarget rt : collectRequests(town)) {
            town = relocateForRequest(town, rt);
        }
        return town;
    }

    private static List<RequestTarget> collectRequests(MCTownState town) {
        List<RequestTarget> out = new ArrayList<>();
        for (ContainerTarget<MCContainer, MCTownItem> chest : town.containers) {
            for (MCTownItem item : chest.getItems()) {
                if (item.isEmpty() || !(item.get() instanceof StockRequestItem)) {
                    continue;
                }
                if (!StockRequestItem.hasRequest(item.getItemNBT())) {
                    continue;
                }
                out.add(new RequestTarget(chest.getBlockPos(), StockRequestItem.getRequest(item.getItemNBT())));
            }
        }
        return out;
    }

    private static MCTownState relocateForRequest(MCTownState town, RequestTarget rt) {
        Ingredient ingredient = rt.request().asIngredient();
        Predicate<MCTownItem> isRequestedIngredient = item -> isRelocatable(item, ingredient);
        for (int moved = 0; moved < MAX_RELOCATIONS_PER_REQUEST; moved++) {
            if (freeSlotsAt(town, rt.chestPos()) <= MIN_FREE_SLOTS_IN_TARGET) {
                break;
            }
            MCTownState next = town.withItemRelocatedTo(isRequestedIngredient, rt.chestPos());
            if (next == null) {
                break;
            }
            town = next;
        }
        return town;
    }

    private static boolean isRelocatable(MCTownItem item, Ingredient ingredient) {
        if (item.isEmpty() || item.get() instanceof StockRequestItem) {
            return false;
        }
        return ingredient.test(item.toQTItemStack());
    }

    private static int freeSlotsAt(MCTownState town, BlockPos pos) {
        for (ContainerTarget<MCContainer, MCTownItem> chest : town.containers) {
            if (chest.getBlockPos().equals(pos)) {
                return chest.countEmptySlots();
            }
        }
        return -1;
    }
}
