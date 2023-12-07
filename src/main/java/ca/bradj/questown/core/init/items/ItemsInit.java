package ca.bradj.questown.core.init.items;

import ca.bradj.questown.Questown;
import ca.bradj.questown.blocks.*;
import ca.bradj.questown.core.init.BlocksInit;
import ca.bradj.questown.items.GathererMap;
import ca.bradj.questown.items.KnowledgeMetaItem;
import ca.bradj.questown.items.TownDoorItem;
import ca.bradj.questown.items.TownFenceGateItem;
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
                    BlocksInit.COBBLESTONE_TOWN_FLAG.get(),
                    TownFlagBlock.ITEM_PROPS
            )
    );

    public static final RegistryObject<Item> WELCOME_MAT_BLOCK = ITEMS.register(
            WelcomeMatBlock.ITEM_ID,
            () -> new BlockItem(
                    BlocksInit.WELCOME_MAT_BLOCK.get(),
                    Questown.DEFAULT_ITEM_PROPS
            )
    );

    public static final RegistryObject<Item> JOB_BOARD_BLOCK = ITEMS.register(
            JobBoardBlock.ITEM_ID,
            () -> new BlockItem(
                    BlocksInit.JOB_BOARD_BLOCK.get(),
                    Questown.DEFAULT_ITEM_PROPS
            )
    );

    public static final RegistryObject<Item> BREAD_OVEN_BLOCK = ITEMS.register(
            BreadOvenBlock.ITEM_ID,
            () -> new BlockItem(
                    BlocksInit.BREAD_OVEN_BLOCK.get(),
                    Questown.DEFAULT_ITEM_PROPS
            )
    );

    public static final RegistryObject<Item> ORE_PROCESSING_BLOCK = ITEMS.register(
            OreProcessingBlock.ITEM_ID,
            () -> new BlockItem(
                    BlocksInit.ORE_PROCESSING_BLOCK.get(),
                    Questown.DEFAULT_ITEM_PROPS
            )
    );

    public static final RegistryObject<Item> BLACKSMITHS_TABLE_BLOCK = ITEMS.register(
            BlacksmithsTableBlock.ITEM_ID,
            () -> new BlockItem(
                    BlocksInit.BLACKSMITHS_TABLE_BLOCK.get(),
                    Questown.DEFAULT_ITEM_PROPS
            )
    );

    public static final RegistryObject<Item> TOWN_DOOR = ITEMS.register(
            TownDoorItem.ITEM_ID,
            TownDoorItem::new
    );

    public static final RegistryObject<Item> TOWN_FENCE_GATE = ITEMS.register(
            TownFenceGateItem.ITEM_ID,
            TownFenceGateItem::new
    );


    public static final RegistryObject<Item> GATHERER_MAP = ITEMS.register(
            GathererMap.ITEM_ID,
            GathererMap::new
    );


    public static final RegistryObject<Item> KNOWLEDGE = ITEMS.register(
            KnowledgeMetaItem.ITEM_ID,
            KnowledgeMetaItem::new
    );

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
