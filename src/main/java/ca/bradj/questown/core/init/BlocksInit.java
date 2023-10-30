package ca.bradj.questown.core.init;

import ca.bradj.questown.Questown;
import ca.bradj.questown.blocks.*;
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

    public static final RegistryObject<Block> WELCOME_MAT_BLOCK = BLOCKS.register(
            WelcomeMatBlock.ITEM_ID, WelcomeMatBlock::new
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
}