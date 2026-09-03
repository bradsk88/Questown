package ca.bradj.questown.core.init;

import ca.bradj.questown.core.init.items.ItemsInit;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

public class ModItemGroup {

    public static final CreativeModeTab QUESTOWN_GROUP = new CreativeModeTab.Builder(
            CreativeModeTab.Row.BOTTOM,
            0
    )
            .title(Component.translatable("itemGroup.questown"))
            .icon(() -> new ItemStack(ItemsInit.TOWN_FLAG_BLOCK.get()))
            .displayItems((parameters, output) ->
                    ItemsInit.ITEMS.getEntries().forEach(reg -> output.accept(reg.get())))
            .build();
}
