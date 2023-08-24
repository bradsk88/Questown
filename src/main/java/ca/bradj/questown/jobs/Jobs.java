package ca.bradj.questown.jobs;

import ca.bradj.questown.integration.minecraft.MCHeldItem;
import com.google.common.collect.ImmutableList;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

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


    public static <H extends HeldItem<H, ?> & GathererJournal.Item<H>> void setItemsOnJournal(
            Iterable<H> mcTownItemStream,
            List<H> inventory,
            int capacity
    ) {
        ImmutableList.Builder<H> b = ImmutableList.builder();
        mcTownItemStream.forEach(b::add);
        ImmutableList<H> initItems = b.build();
        if (initItems.size() != capacity) {
            throw new IllegalArgumentException(String.format(
                    "Argument to setItems is wrong length. Should be %s", capacity
            ));
        }
        inventory.clear();
        inventory.addAll(initItems);

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
}
