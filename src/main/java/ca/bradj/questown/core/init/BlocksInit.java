package ca.bradj.questown.core.init;

import ca.bradj.questown.Questown;
import ca.bradj.questown.blocks.TownFlagBlock;
import ca.bradj.questown.core.materials.WallType;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class BlocksInit {

	public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS,
			Questown.MODID);

	public static final RegistryObject<Block> COBBLESTONE_TOWN_FLAG = BLOCKS.register(
			TownFlagBlock.itemId(WallType.COBBLESTONE), TownFlagBlock::new
	);
}