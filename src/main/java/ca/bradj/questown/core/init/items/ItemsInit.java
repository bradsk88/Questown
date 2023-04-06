package ca.bradj.questown.core.init.items;

import ca.bradj.questown.Questown;
import ca.bradj.questown.blocks.TownFlagBlock;
import ca.bradj.questown.core.init.BlocksInit;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ItemsInit {
	private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Questown.MODID);

	public static final RegistryObject<Item> TOWN_FLAG_BLOCK = ITEMS.register(
			TownFlagBlock.ITEM_ID,
			() -> new BlockItem(
					BlocksInit.TOWN_FLAG.get(),
					TownFlagBlock.ITEM_PROPS
			)
	);
	public static void register(IEventBus bus) {
		ITEMS.register(bus);
	}
}
