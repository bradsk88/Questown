package ca.bradj.questown.core.init;

import ca.bradj.questown.Questown;
import ca.bradj.questown.blocks.*;
import ca.bradj.questown.blocks.entity.BlockAsRoomEntity;
import ca.bradj.questown.core.materials.WallType;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class BlocksInit {

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(
            ForgeRegistries.BLOCKS,
            Questown.MODID
    );

    public static final RegistryObject<Block> COBBLESTONE_TOWN_FLAG = BLOCKS.register(
            TownFlagBlock.itemId(WallType.COBBLESTONE), TownFlagBlock::new
    );
    public static final RegistryObject<Block> FALSE_WALL_BLOCK = BLOCKS.register(
            FalseWallBlock.ID, FalseWallBlock::new
    );
    public static final RegistryObject<Block> FALSE_DOOR_BLOCK = BLOCKS.register(
            FalseDoorBlock.ID, FalseDoorBlock::new
    );

    public static final RegistryObject<Block> WELCOME_MAT_BLOCK = BLOCKS.register(
            WelcomeMatBlock.ITEM_ID, WelcomeMatBlock::new
    );
    public static final RegistryObject<Block> JOB_BOARD_BLOCK = BLOCKS.register(
            JobBoardBlock.ITEM_ID, JobBoardBlock::new
    );
    public static final RegistryObject<Block> BREAD_OVEN_BLOCK = BLOCKS.register(
            BreadOvenBlock.ITEM_ID, BreadOvenBlock::new
    );
    public static final RegistryObject<Block> ORE_PROCESSING_BLOCK = BLOCKS.register(
            OreProcessingBlock.ITEM_ID, OreProcessingBlock::new
    );
    public static final RegistryObject<Block> BLACKSMITHS_TABLE_BLOCK = BLOCKS.register(
            BlacksmithsTableBlock.ITEM_ID, BlacksmithsTableBlock::new
    );
    public static final RegistryObject<Block> PLATE_BLOCK = BLOCKS.register(
            PlateBlock.ITEM_ID, PlateBlock::new
    );
    public static final RegistryObject<Block> SOUP_POT = BLOCKS.register(
            SoupPotBlock.ITEM_ID, SoupPotBlock::new
    );
    public static final RegistryObject<Block> SOUP_POT_SMALL = BLOCKS.register(
            SmallSoupPotBlock.ITEM_ID, SmallSoupPotBlock::new
    );
    public static final RegistryObject<Block> HOSPITAL_BED = BLOCKS.register(
            HospitalBedBlock.ITEM_ID, HospitalBedBlock::new
    );
    public static final RegistryObject<Block> BOWL_RACK = BLOCKS.register(
            BowlRackBlock.ITEM_ID, BowlRackBlock::new
    );
    public static final RegistryObject<Block> FOOD_DISPLAY = BLOCKS.register(
            FoodDisplayBlock.ITEM_ID, FoodDisplayBlock::new
    );
    public static final RegistryObject<Block> SEED_BIN = BLOCKS.register(
            SeedBinBlock.ITEM_ID, SeedBinBlock::new
    );
    public static final RegistryObject<Block> BLOCK_OF_PROGRESS = BLOCKS.register(
            BlockOfProgress.ITEM_ID, BlockOfProgress::new
    );
    public static final RegistryObject<Block> FISHING_STATION_BLOCK = BlockAsRoomEntity.register(BLOCKS.register(
            FishingStationBlock.ITEM_ID, FishingStationBlock::new
    ));
}