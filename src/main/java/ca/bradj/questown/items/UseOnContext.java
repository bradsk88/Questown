package ca.bradj.questown.items;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;

class UseOnContext extends net.minecraft.world.item.context.UseOnContext {

    public UseOnContext(
            net.minecraft.world.item.context.UseOnContext reference,
            ItemStack swappedItem
    ) {
        super(
                reference.getLevel(), reference.getPlayer(), reference.getHand(), swappedItem,
                new BlockHitResult(
                        reference.getClickLocation(), reference.getClickedFace(),
                        reference.getClickedPos(), reference.isInside()
                )
        );
    }
}
