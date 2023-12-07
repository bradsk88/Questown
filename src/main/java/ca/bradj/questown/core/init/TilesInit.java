package ca.bradj.questown.core.init;

import ca.bradj.questown.Questown;
import ca.bradj.questown.blocks.JobBoardBlock;
import ca.bradj.questown.blocks.WelcomeMatBlock;
import ca.bradj.questown.town.TownFlagBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

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

}