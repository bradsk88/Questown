package ca.bradj.questown.core.init;

import ca.bradj.questown.Questown;
import ca.bradj.questown.jobs.GathererEntity;
import ca.bradj.questown.town.TownFlagBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class TilesInit {
    public static final DeferredRegister<BlockEntityType<?>> TILES = DeferredRegister.create(
            ForgeRegistries.BLOCK_ENTITIES, Questown.MODID
    );

    public static final RegistryObject<BlockEntityType<TownFlagBlockEntity>> TOWN_FLAG = TILES.register(
            TownFlagBlockEntity.ID, () -> BlockEntityType.Builder.of(
                    TownFlagBlockEntity::new, BlocksInit.COBBLESTONE_TOWN_FLAG.get()
            ).build(null)
    );

    public static final RegistryObject<BlockEntityType<GathererEntity>> GATHERER = TILES.register(
            GathererEntity.ID, () -> BlockEntityType.Builder.of(
                    GathererEntity::new, BlocksInit.GATHERER_DUMMY.get()
            ).build(null)
    );

}