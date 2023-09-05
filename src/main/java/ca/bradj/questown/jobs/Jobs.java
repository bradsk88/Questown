package ca.bradj.questown.jobs;

import ca.bradj.questown.integration.minecraft.MCHeldItem;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class Jobs {
    public static ImmutableList<ItemStack> getItems(Job<MCHeldItem, ?> job) {
        ImmutableList.Builder<ItemStack> b = ImmutableList.builder();
        Container inventory = job.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            b.add(inventory.getItem(i));
        }
        return b.build();
    }

    public static boolean isUnchanged(
            Container container,
            ImmutableList<MCHeldItem> items
    ) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (!container.getItem(i).is(items.get(i).get().get())) {
                return false;
            }
        }
        return true;
    }

    public static boolean isCloseTo(
            @NotNull BlockPos entityPos,
            @NotNull BlockPos targetPos
    ) {
        double d = targetPos.distToCenterSqr(entityPos.getX(), entityPos.getY(), entityPos.getZ());
        return d < 5;
    }

    public static boolean isVeryCloseTo(
            Vec3 entityPos,
            @NotNull BlockPos targetPos
    ) {
        double d = targetPos.distToCenterSqr(entityPos.x, entityPos.y, entityPos.z);
        return d < 0.5;
    }

    public static void handleItemChanges(
            Container inventory,
            ImmutableList<MCHeldItem> items
    ) {
        if (Jobs.isUnchanged(inventory, items)) {
            return;
        }

        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).get().equals(inventory.getItem(i).getItem())) {
                continue;
            }
            inventory.setItem(i, new ItemStack(items.get(i).get().get(), 1));
        }
    }
}
