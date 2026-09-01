package ca.bradj.questown.blocks;

import ca.bradj.questown.blocks.entity.ChickenStatueBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Material;

/**
 * Trophy block left where the helper chicken stood when the onboarding arc
 * completes. It is a full solid block for collision and light, but its own
 * model is empty: the block entity renders a stone-colored chicken model over
 * it (see {@code ChickenStatueBlockEntityRenderer}), so the finished chicken
 * looks like a statue rather than a cube.
 *
 * <p>Strength 0.0 with no correct-tool requirement means it breaks by hand
 * and drops itself via {@code blocks/stone_chicken_statue}, so the player can
 * pick the statue up easily.
 */
public class StoneChickenStatue extends Block implements EntityBlock {
    public static final String ITEM_ID = "stone_chicken_statue";

    public StoneChickenStatue() {
        super(Properties
                .of(Material.STONE)
                .strength(0.0f)
                .noOcclusion());
    }

    @Override
    public BlockEntity newBlockEntity(
            BlockPos pos,
            BlockState state
    ) {
        return new ChickenStatueBlockEntity(pos, state);
    }
}
