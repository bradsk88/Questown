package ca.bradj.questown.blocks;

import ca.bradj.questown.mc.Compat;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import ca.bradj.questown.mc.CompatBlockColor;
import net.minecraft.world.phys.BlockHitResult;

public class MineshaftBlock extends RoomBlock {
    public static final MapCodec<MineshaftBlock> CODEC = MapCodec.unit(MineshaftBlock::new);

    public static final String ITEM_ID = "mineshaft";

    public MineshaftBlock(
    ) {
        super(Compat.blockProps(CompatBlockColor.STONE).strength(1.0F, 10.0F).noOcclusion().lightLevel(b -> 15));
    }

    public InteractionResult useWithoutItem(
            BlockState p_60503_,
            Level p_60504_,
            BlockPos p_60505_,
            Player p_60506_,
            BlockHitResult p_60508_
    ) {
        if (!(p_60506_ instanceof ServerPlayer sp)) {
            return InteractionResult.CONSUME;
        }
        Compat.sendMessage(sp, Compat.translatable("message.questown.mineshaft.use"));
        // A deep-cave echo from the shaft: vanilla cave ambience, a random variant each use.
        if (p_60504_ instanceof ServerLevel sl) {
            Compat.playSound(sl, p_60505_, Compat.caveAmbience(), SoundSource.BLOCKS);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public String getId() {
        return ITEM_ID;
    }

    @Override
    public net.minecraft.world.InteractionResult use(
            net.minecraft.world.level.block.state.BlockState qtState,
            net.minecraft.world.level.Level qtLevel,
            net.minecraft.core.BlockPos qtPos,
            net.minecraft.world.entity.player.Player qtPlayer,
            net.minecraft.world.InteractionHand qtHand,
            net.minecraft.world.phys.BlockHitResult qtHit
    ) {
        if (qtHand == net.minecraft.world.InteractionHand.MAIN_HAND) {
            return useWithoutItem(qtState, qtLevel, qtPos, qtPlayer, qtHit);
        }
        return net.minecraft.world.InteractionResult.PASS;
    }
}
