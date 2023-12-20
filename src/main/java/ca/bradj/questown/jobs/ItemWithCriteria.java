package ca.bradj.questown.jobs;

import ca.bradj.questown.jobs.gatherer.NewLeaverWork;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public record ItemWithCriteria(
        ItemStack itemStack,
        @Nullable NewLeaverWork.TagsCriteria tagCriteria
) {
    public static ItemWithCriteria noCriteria(ItemStack defaultInstance) {
        return new ItemWithCriteria(defaultInstance, null);
    }
}
