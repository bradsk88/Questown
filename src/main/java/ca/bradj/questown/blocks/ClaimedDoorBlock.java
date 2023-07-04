package ca.bradj.questown.blocks;

import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.level.material.MaterialColor;

public class ClaimedDoorBlock extends DoorBlock {
    public ClaimedDoorBlock() {
        super(
                BlockBehaviour.Properties.of(Material.WOOD, MaterialColor.WOOD)
                        .strength(0.5F)
        );
    }
}
