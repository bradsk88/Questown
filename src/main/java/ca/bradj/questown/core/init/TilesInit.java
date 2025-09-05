package ca.bradj.questown.core.init;

import ca.bradj.questown.Questown;
import ca.bradj.questown.blocks.*;
import ca.bradj.questown.blocks.entity.*;
import ca.bradj.questown.town.TownFlagBlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

public class TilesInit {
    public static final DeferredRegister<BlockEntityType<?>> TILES = DeferredRegister.create(
            ForgeRegistries.BLOCK_ENTITY_TYPES, Questown.MODID
    );

    public static final RegistryObject<BlockEntityType<TownFlagBlockEntity>> TOWN_FLAG = TILES.register(
            TownFlagBlockEntity.ID, () -> BlockEntityType.Builder.of(
                    TownFlagBlockEntity::new, BlocksInit.COBBLESTONE_TOWN_FLAG.get()
            ).build(null)
    );

    public static final RegistryObject<BlockEntityType<JobBoardBlock.Entity>> JOB_BOARD = TILES.register(
            JobBoardBlock.ITEM_ID, () -> BlockEntityType.Builder.of(
                    JobBoardBlock.Entity::new, BlocksInit.JOB_BOARD_BLOCK.get()
            ).build(null)
    );
    public static final RegistryObject<BlockEntityType<WelcomeMatBlock.Entity>> WELCOME_MAT = TILES.register(
            WelcomeMatBlock.ITEM_ID, () -> BlockEntityType.Builder.of(
                    WelcomeMatBlock.Entity::new, BlocksInit.WELCOME_MAT_BLOCK.get()
            ).build(null)
    );

    public static final RegistryObject<BlockEntityType<PlateBlockEntity>> PLATE = TILES.register(
            PlateBlock.ITEM_ID, () -> BlockEntityType.Builder.of(
                    PlateBlockEntity::new, BlocksInit.PLATE_BLOCK.get()
            ).build(null)
    );

    public static final RegistryObject<BlockEntityType<FoodDisplayEntity>> FOOD_DISPLAY = TILES.register(
            FoodDisplayBlock.ITEM_ID, () -> BlockEntityType.Builder.of(
                    FoodDisplayEntity::new, BlocksInit.FOOD_DISPLAY.get()
            ).build(null)
    );

    public static final RegistryObject<BlockEntityType<HospitalBedBlockEntity>> HOSPITAL_BED = TILES.register(
            HospitalBedBlock.ITEM_ID, () -> BlockEntityType.Builder.of(
                    HospitalBedBlockEntity::new, BlocksInit.HOSPITAL_BED.get()
            ).build(null)
    );
    public static final RegistryObject<BlockEntityType<BowlRackBlockEntity>> BOWL_RACK = TILES.register(
            BowlRackBlock.ITEM_ID, () -> BlockEntityType.Builder.of(
                    BowlRackBlockEntity::new, BlocksInit.BOWL_RACK.get()
            ).build(null)
    );
    public static final RegistryObject<BlockEntityType<SeedBinBlockEntity>> SEED_BIN = TILES.register(
            SeedBinBlock.ITEM_ID, () -> BlockEntityType.Builder.of(
                    SeedBinBlockEntity::new, BlocksInit.SEED_BIN.get()
            ).build(null)
    );
    public static final RegistryObject<BlockEntityType<AxeRackBlockEntity>> AXE_RACK = TILES.register(
            AxeRackBlock.ITEM_ID, () -> BlockEntityType.Builder.of(
                    AxeRackBlockEntity::new, BlocksInit.AXE_RACK.get()
            ).build(null)
    );
    public static final RegistryObject<BlockEntityType<BlockAsRoomEntity>> BLOCK_AS_ROOM = TILES.register(
            "block_as_room", () -> {
                Block[] array = BlockAsRoomEntity.ALL.stream().map(Supplier::get).toArray(Block[]::new);
                return BlockEntityType.Builder.of(BlockAsRoomEntity::new, array).build(null);
            }
    );

}