package ca.bradj.questown.blocks;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Material;

/**
 * Placeholder statue block used by the helper-chicken onboarding arc.
 *
 * <p>The block is pickaxe-breakable and drops itself via the loot table
 * at {@code data/questown/loot_tables/blocks/stone_chicken_statue.json}.
 * Bespoke geometry is deferred — U1 ships a plain stone-textured cube.
 */
public class StoneChickenStatue extends Block {
    public static final String ITEM_ID = "stone_chicken_statue";

    public StoneChickenStatue() {
        super(Properties
                .of(Material.STONE)
                .strength(1.5f)
                .requiresCorrectToolForDrops());
    }
}
