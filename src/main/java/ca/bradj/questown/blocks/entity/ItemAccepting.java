package ca.bradj.questown.blocks.entity;

import net.minecraft.world.item.ItemStack;

public interface ItemAccepting {
    boolean setItem(
            ItemStack item,
            int index
    );
}
