package ca.bradj.questown.core.init.items;

import ca.bradj.questown.Questown;
import ca.bradj.questown.blocks.*;
import ca.bradj.questown.core.init.BlocksInit;
import ca.bradj.questown.items.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

@Mod.EventBusSubscriber(modid = Questown.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
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
    public static final RegistryObject<Item> FALSE_WALL_BLOCK = ITEMS.register(
            FalseWallBlock.ID,
            FalseWallItem::new
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

    public static final RegistryObject<Item> PLATE_BLOCK = ITEMS.register(
            PlateBlock.ITEM_ID,
            () -> new BlockItem(
                    BlocksInit.PLATE_BLOCK.get(),
                    Questown.DEFAULT_ITEM_PROPS
            )
    );

    public static final RegistryObject<Item> SOUP_POT_BLOCK = ITEMS.register(
            SoupPotBlock.ITEM_ID,
            () -> new BlockItem(
                    BlocksInit.SOUP_POT.get(),
                    Questown.DEFAULT_ITEM_PROPS
            )
    );
    public static final RegistryObject<Item> SMALL_SOUP_POT_BLOCK = ITEMS.register(
            SmallSoupPotBlock.ITEM_ID,
            () -> new BlockItem(
                    BlocksInit.SOUP_POT_SMALL.get(),
                    Questown.DEFAULT_ITEM_PROPS
            )
    );

    public static final RegistryObject<Item> TOWN_DOOR = ITEMS.register(
            TownDoorItem.ITEM_ID,
            TownDoorItem::new
    );

    public static final RegistryObject<Item> TOWN_DOOR_TESTER = ITEMS.register(
            TownDoorTestItem.ITEM_ID,
            TownDoorTestItem::new
    );

    public static final RegistryObject<Item> FALSE_DOOR = ITEMS.register(
            FalseDoorItem.ITEM_ID,
            FalseDoorItem::new
    );

    public static final RegistryObject<Item> TOWN_FENCE_GATE = ITEMS.register(
            TownFenceGateItem.ITEM_ID,
            TownFenceGateItem::new
    );


    public static final RegistryObject<Item> GATHERER_MAP = ITEMS.register(
            GathererMap.ITEM_ID,
            GathererMap::new
    );

    public static final RegistryObject<Item> TOWN_WAND = ITEMS.register(
            TownWand.ITEM_ID,
            TownWand::new
    );

    @SubscribeEvent
    public static void onInteractBlock(PlayerInteractEvent.RightClickBlock event) {
        final var level = event.getWorld();
        if (level.isClientSide) return; // Note this is fired both client and server side
        final var itemUsed = event.getItemStack().getItem();
        if (itemUsed instanceof TownWand item) {
            item.onRightClicked(
                    () -> (ServerPlayer) event.getPlayer(),
                    (ServerLevel) event.getWorld(),
                    event.getPos(),
                    event.getItemStack()
            );
        }
    }

    public static final RegistryObject<Item> KNOWLEDGE = ITEMS.register(
            KnowledgeMetaItem.ITEM_ID,
            KnowledgeMetaItem::new
    );


    public static final RegistryObject<Item> EFFECT = ITEMS.register(
            EffectMetaItem.ITEM_ID,
            EffectMetaItem::new
    );

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
