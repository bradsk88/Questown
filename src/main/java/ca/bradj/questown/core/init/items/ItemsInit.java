package ca.bradj.questown.core.init.items;

import ca.bradj.questown.loader.Holder;
import ca.bradj.questown.loader.HolderRegistry;
import ca.bradj.questown.loader.Loader;

import ca.bradj.questown.Questown;
import ca.bradj.questown._vanilla.blocks.SaplingTesterBlock;
import ca.bradj.questown.blocks.*;
import ca.bradj.questown.core.init.BlocksInit;
import ca.bradj.questown.items.*;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.Util;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

@Mod.EventBusSubscriber(modid = Questown.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ItemsInit {
    public static final HolderRegistry<Item> ITEMS = Loader.items();

    public static final Holder<Item> TOWN_FLAG_BLOCK = ITEMS.register(
            TownFlagBlock.ITEM_ID,
            () -> new BlockItem(BlocksInit.COBBLESTONE_TOWN_FLAG.get(), TownFlagBlock.ITEM_PROPS)
    );

    public static final Holder<Item> WELCOME_MAT_BLOCK = ITEMS.register(
            WelcomeMatBlock.ITEM_ID,
            () -> new BlockItem(BlocksInit.WELCOME_MAT_BLOCK.get(), Questown.DEFAULT_ITEM_PROPS)
    );
    public static final Holder<Item> RELOCATION_DEED = ITEMS.register(
            RelocationDeedItem.ITEM_ID,
            RelocationDeedItem::new
    );
    public static final Holder<Item> FALSE_WALL_BLOCK = ITEMS.register(FalseWallBlock.ID, FalseWallItem::new);

    public static final Holder<Item> JOB_BOARD_BLOCK = ITEMS.register(
            JobBoardBlock.ITEM_ID,
            () -> new BlockItem(BlocksInit.JOB_BOARD_BLOCK.get(), Questown.DEFAULT_ITEM_PROPS)
    );

    public static final Holder<Item> BREAD_OVEN_BLOCK = ITEMS.register(
            BreadOvenBlock.ITEM_ID,
            () -> new BlockItem(BlocksInit.BREAD_OVEN_BLOCK.get(), Questown.DEFAULT_ITEM_PROPS)
    );

    public static final Holder<Item> ORE_PROCESSING_BLOCK = ITEMS.register(
            OreProcessingBlock.ITEM_ID,
            () -> new BlockItem(BlocksInit.ORE_PROCESSING_BLOCK.get(), Questown.DEFAULT_ITEM_PROPS)
    );

    public static final Holder<Item> BLACKSMITHS_TABLE_BLOCK = ITEMS.register(
            BlacksmithsTableBlock.ITEM_ID,
            () -> new BlockItem(BlocksInit.BLACKSMITHS_TABLE_BLOCK.get(), Questown.DEFAULT_ITEM_PROPS)
    );

    public static final Holder<Item> PLATE_BLOCK = ITEMS.register(
            PlateBlock.ITEM_ID,
            () -> new BlockItem(BlocksInit.PLATE_BLOCK.get(), Questown.DEFAULT_ITEM_PROPS)
    );

    public static final Holder<Item> SOUP_POT_BLOCK = ITEMS.register(
            SoupPotBlock.ITEM_ID,
            () -> new BlockItem(BlocksInit.SOUP_POT.get(), Questown.DEFAULT_ITEM_PROPS)
    );

    public static final Holder<Item> BOWL_RACK_BLOCK = ITEMS.register(
            BowlRackBlock.ITEM_ID,
            () -> new BlockItem(BlocksInit.BOWL_RACK.get(), Questown.DEFAULT_ITEM_PROPS)
    );

    public static final Holder<Item> SEED_BIN_BLOCK = ITEMS.register(
            SeedBinBlock.ITEM_ID,
            () -> new BlockItem(BlocksInit.SEED_BIN.get(), Questown.DEFAULT_ITEM_PROPS)
    );
    public static final Holder<Item> AXE_RACK_BLOCK = ITEMS.register(
            AxeRackBlock.ITEM_ID,
            () -> new BlockItem(BlocksInit.AXE_RACK.get(), Questown.DEFAULT_ITEM_PROPS)
    );

    public static final Holder<Item> FOOD_DISPLAY_BLOCK = ITEMS.register(
            FoodDisplayBlock.ITEM_ID,
            () -> new BlockItem(BlocksInit.FOOD_DISPLAY.get(), Questown.DEFAULT_ITEM_PROPS)
    );
    public static final Holder<Item> SMALL_SOUP_POT_BLOCK = ITEMS.register(
            SmallSoupPotBlock.ITEM_ID,
            () -> new BlockItem(BlocksInit.SOUP_POT_SMALL.get(), Questown.DEFAULT_ITEM_PROPS)
    );

    public static final Holder<Item> BLOCK_OF_PROGRESS = ITEMS.register(
            BlockOfProgress.ITEM_ID,
            () -> new BlockOfProgressItem(Questown.DEFAULT_ITEM_PROPS)
    );

    public static final Holder<Item> FISHING_STATION_BLOCK = ITEMS.register(
            FishingStationBlock.ITEM_ID,
            () -> new BlockItem(BlocksInit.FISHING_STATION_BLOCK.get(), Questown.DEFAULT_ITEM_PROPS)
    );

    /**
     * @deprecated Use Town Wand
     */
    @Deprecated(forRemoval = true)
    public static final Holder<Item> MINESHAFT_BLOCK = ITEMS.register(
            MineshaftBlock.ITEM_ID,
            () -> new BlockItem(BlocksInit.MINESHAFT.get(), Questown.DEFAULT_ITEM_PROPS)
    );

    public static final Holder<Item> TOWN_DOOR = ITEMS.register(TownDoorItem.ITEM_ID, TownDoorItem::new);

    public static final Holder<Item> TOWN_DOOR_TESTER = ITEMS.register(
            TownDoorTestItem.ITEM_ID,
            TownDoorTestItem::new
    );

    public static final Holder<Item> FALSE_DOOR = ITEMS.register(FalseDoorItem.ITEM_ID, FalseDoorItem::new);

    /**
     * @deprecated Use Town Wand
     */
    @Deprecated(forRemoval = true)
    public static final Holder<Item> TOWN_FENCE_GATE = ITEMS.register(
            TownFenceGateItem.ITEM_ID,
            TownFenceGateItem::new
    );


    public static final Holder<Item> GATHERER_MAP = ITEMS.register(GathererMap.ITEM_ID, GathererMap::new);

    public static final Holder<Item> TOWN_WAND = ITEMS.register(TownWand.ITEM_ID, TownWand::new);

    public static final Holder<Item> STOCK_REQUEST = ITEMS.register(
            StockRequestItem.ITEM_ID,
            StockRequestItem::new
    );

    public static final Holder<Item> STOCK_REQUEST_CLIPBOARD = ITEMS.register(
            StockRequestClipboardItem.ITEM_ID,
            StockRequestClipboardItem::new
    );

    /** Neutral interaction core; the per-loader {@code @SubscribeEvent} binder (see {@code loader.neo.ItemsInteract}) calls this. */
    public static boolean onInteractRightClick(
            net.minecraft.server.level.ServerLevel level,
            net.minecraft.server.level.ServerPlayer sp,
            net.minecraft.core.BlockPos pos,
            net.minecraft.world.item.ItemStack stack) {
        if (sp == null) {
            return false;
        }
        if (level.getBlockEntity(pos) instanceof TownFlagBlockEntity) {
            return false;
        }
        final var itemUsed = stack.getItem();
        boolean cancel = false;
        if (itemUsed instanceof TownWand item) {
            item.onRightClicked(() -> sp, level, pos, stack);
            cancel = true;
        }
        if (itemUsed instanceof BlockItem bi && bi.getBlock() instanceof RoomBlock) {
            TownFlagBlockEntity parent = TownFlagBlock.GetParentFromNBT(level, stack);
            if (parent == null) {
                Util.onScreenText(() -> sp, "message.questown.room_block.how_to_flag", Compat.getItemName(ItemsInit.TOWN_WAND.get()));
            }
        }
        return cancel;
    }

    public static final Holder<Item> HOSPITAL_BED = ITEMS.register(
            HospitalBedBlock.ITEM_ID,
            () -> new HospitalBedItem(BlocksInit.HOSPITAL_BED.get(), Questown.DEFAULT_ITEM_PROPS.stacksTo(1))
    );

    public static final Holder<Item> STONE_CHICKEN_STATUE = ITEMS.register(
            StoneChickenStatue.ITEM_ID,
            () -> new BlockItem(BlocksInit.STONE_CHICKEN_STATUE.get(), Questown.DEFAULT_ITEM_PROPS)
    );

    public static final Holder<Item> WORLDLY_SEEDS = ITEMS.register(
            WorldlySeeds.ITEM_ID,
            WorldlySeeds::new
    );

    // Vanilla items

    public static final Holder<Item> SAPLING_TESTER_BLOCK = ITEMS.register(
            SaplingTesterBlock.ITEM_ID,
            () -> new BlockItem(BlocksInit.SAPLING_TESTER_BLOCK.get(), Questown.DEFAULT_ITEM_PROPS)
    );

    public static void boot() {}
}
