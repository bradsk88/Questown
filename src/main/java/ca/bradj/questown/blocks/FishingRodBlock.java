package ca.bradj.questown.blocks;

import ca.bradj.questown._vanilla.entities.EntitiesInit;
import ca.bradj.questown._vanilla.entities.FishingHook;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.level.material.MaterialColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class FishingRodBlock extends Block {
    public static final String ITEM_ID = "fishing_rod_block";

    public FishingRodBlock(
    ) {
        super(Properties.of(Material.WOOD, MaterialColor.WOOD).strength(1.0F, 10.0F).noOcclusion());
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
        if (!(p_60504_ instanceof ServerLevel sl)) {
            return super.use(p_60503_, p_60504_, p_60505_, p_60506_, p_60507_, p_60508_);
        }
        FishingHook e = EntitiesInit.FISHIN_HOOK.get().create(sl);
        if (e == null) {
            return super.use(p_60503_, p_60504_, p_60505_, p_60506_, p_60507_, p_60508_);
        }
        e.setOwner(p_60506_, getAttachPoint(p_60505_));
        Vec3 push = Vec3.atCenterOf(p_60505_);
        push = push.subtract(Vec3.atCenterOf(p_60506_.blockPosition()));
        push = push.normalize();
        push = push.multiply(3, 1, 3);
        e.setPos(Vec3.atBottomCenterOf(p_60505_.offset(push.x, 0, push.z)));
        sl.addFreshEntity(e);
        return InteractionResult.CONSUME;
    }

    private BlockPos getAttachPoint(BlockPos p_60505_) {
        return p_60505_.above();
    }
}
