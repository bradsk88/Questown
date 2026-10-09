package ca.bradj.questown.core.init;

import ca.bradj.questown.loader.Holder;
import ca.bradj.questown.loader.HolderRegistry;
import ca.bradj.questown.loader.Loader;

import ca.bradj.questown._vanilla.blocks.SaplingTesterBlock;
import ca.bradj.questown.blocks.*;
import ca.bradj.questown.blocks.entity.BlockAsRoomEntity;
import ca.bradj.questown.core.materials.WallType;
import net.minecraft.world.level.block.Block;

public class BlocksInit {

    public static final HolderRegistry<Block> BLOCKS = Loader.blocks();

    public static final Holder<Block> COBBLESTONE_TOWN_FLAG = BLOCKS.register(
            TownFlagBlock.itemId(WallType.COBBLESTONE), TownFlagBlock::new
    );
    public static final Holder<Block> FALSE_WALL_BLOCK = BLOCKS.register(
            FalseWallBlock.ID, FalseWallBlock::new
    );
    public static final Holder<Block> FALSE_DOOR_BLOCK = BLOCKS.register(
            FalseDoorBlock.ID, FalseDoorBlock::new
    );

    public static final Holder<Block> WELCOME_MAT_BLOCK = BLOCKS.register(
            WelcomeMatBlock.ITEM_ID, WelcomeMatBlock::new
    );
    public static final Holder<Block> JOB_BOARD_BLOCK = BlockAsRoomEntity.register(BLOCKS.register(
            JobBoardBlock.ITEM_ID, JobBoardBlock::new
    ));
    public static final Holder<Block> BREAD_OVEN_BLOCK = BLOCKS.register(
            BreadOvenBlock.ITEM_ID, BreadOvenBlock::new
    );
    public static final Holder<Block> ORE_PROCESSING_BLOCK = BLOCKS.register(
            OreProcessingBlock.ITEM_ID, OreProcessingBlock::new
    );
    public static final Holder<Block> BLACKSMITHS_TABLE_BLOCK = BLOCKS.register(
            BlacksmithsTableBlock.ITEM_ID, BlacksmithsTableBlock::new
    );
    public static final Holder<Block> PLATE_BLOCK = BLOCKS.register(
            PlateBlock.ITEM_ID, PlateBlock::new
    );
    public static final Holder<Block> SOUP_POT = BLOCKS.register(
            SoupPotBlock.ITEM_ID, SoupPotBlock::new
    );
    public static final Holder<Block> SOUP_POT_SMALL = BLOCKS.register(
            SmallSoupPotBlock.ITEM_ID, SmallSoupPotBlock::new
    );
    public static final Holder<Block> HOSPITAL_BED = BLOCKS.register(
            HospitalBedBlock.ITEM_ID, HospitalBedBlock::new
    );
    public static final Holder<Block> BOWL_RACK = BLOCKS.register(
            BowlRackBlock.ITEM_ID, BowlRackBlock::new
    );
    public static final Holder<Block> FOOD_DISPLAY = BLOCKS.register(
            FoodDisplayBlock.ITEM_ID, FoodDisplayBlock::new
    );
    public static final Holder<Block> SEED_BIN = BLOCKS.register(
            SeedBinBlock.ITEM_ID, SeedBinBlock::new
    );
    public static final Holder<Block> AXE_RACK = BLOCKS.register(
            AxeRackBlock.ITEM_ID, AxeRackBlock::new
    );
    public static final Holder<Block> BLOCK_OF_PROGRESS = BLOCKS.register(
            BlockOfProgress.ITEM_ID, BlockOfProgress::new
    );
    public static final Holder<Block> FISHING_STATION_BLOCK = BlockAsRoomEntity.register(BLOCKS.register(
            FishingStationBlock.ITEM_ID, FishingStationBlock::new
    ));

    public static final Holder<Block> MINESHAFT = BlockAsRoomEntity.register(BLOCKS.register(
            MineshaftBlock.ITEM_ID, MineshaftBlock::new
    ));

    public static final Holder<Block> STONE_CHICKEN_STATUE = BLOCKS.register(
            StoneChickenStatue.ITEM_ID, StoneChickenStatue::new
    );

    // Vanilla Blocks

    public static final Holder<Block> SAPLING_TESTER_BLOCK = BLOCKS.register(
            SaplingTesterBlock.ITEM_ID, SaplingTesterBlock::new
    );


    public static void boot() {}
}
