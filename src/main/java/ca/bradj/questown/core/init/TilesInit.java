package ca.bradj.questown.core.init;

import ca.bradj.questown.Questown;
import ca.bradj.questown.blocks.TownFlagBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class TilesInit {
    public static final DeferredRegister<BlockEntityType<?>> TILES = DeferredRegister.create(
            ForgeRegistries.BLOCK_ENTITIES, Questown.MODID
    );

    public static final RegistryObject<BlockEntityType<TownFlagBlock.Entity>> TOWN_FLAG = TILES.register(
            TownFlagBlock.Entity.ID, () -> BlockEntityType.Builder.of(
                    TownFlagBlock.Entity::new, BlocksInit.TOWN_FLAG.get()
            ).build(null)
    );

}