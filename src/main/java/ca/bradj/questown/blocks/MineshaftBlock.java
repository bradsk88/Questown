package ca.bradj.questown.blocks;

import ca.bradj.questown.mc.Compat;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.level.material.MaterialColor;
import net.minecraft.world.phys.BlockHitResult;

public class MineshaftBlock extends RoomBlock {
    public static final String ITEM_ID = "mineshaft";

    public MineshaftBlock(
    ) {
        super(Properties.of(Material.STONE, MaterialColor.STONE).strength(1.0F, 10.0F).noOcclusion().lightLevel(b -> 15));
    }

    @Override
    public InteractionResult use(
            BlockState p_60503_,
            Level p_60504_,
            BlockPos p_60505_,
            Player p_60506_,
            InteractionHand p_60507_,
            BlockHitResult p_60508_
    ) {
        if (!(p_60506_ instanceof ServerPlayer sp)) {
            return InteractionResult.CONSUME;
        }
        Compat.sendMessage(sp, Compat.translatable("message.questown.mineshaft.use"));
        return InteractionResult.CONSUME;
    }

    @Override
    public String getId() {
        return ITEM_ID;
    }
}
