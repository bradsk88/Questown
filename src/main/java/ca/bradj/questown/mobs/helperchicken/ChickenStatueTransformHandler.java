package ca.bradj.questown.mobs.helperchicken;

import ca.bradj.questown.core.init.BlocksInit;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/** The helper chicken's last lesson: hearts, a stone statue where it stood, the chicken removed, and the lessons marked complete. */
public final class ChickenStatueTransformHandler {

    private ChickenStatueTransformHandler() {
    }

    public static void transform(
            HelperChickenEntity chicken,
            TownFlagBlockEntity flag
    ) {
        if (!(chicken.getLevel() instanceof ServerLevel sl)) {
            return;
        }
        BlockPos statuePos = statuePos(sl, chicken.blockPosition());
        placeStatue(sl, statuePos);
        playEffects(sl, statuePos);
        markArcComplete(flag);
        chicken.discard();
    }

    /** Where the chicken stood, unless something solid is there. A flower is replaced. */
    static BlockPos statuePos(
            BlockGetter level,
            BlockPos at
    ) {
        if (canHoldStatue(level.getBlockState(at))) {
            return at;
        }
        BlockPos above = at.above();
        if (canHoldStatue(level.getBlockState(above))) {
            return above;
        }
        return at;
    }

    private static boolean canHoldStatue(BlockState state) {
        return ca.bradj.questown.mc.Compat.canBeReplaced(state) || state.is(BlockTags.SMALL_FLOWERS);
    }

    private static void placeStatue(
            ServerLevel level,
            BlockPos pos
    ) {
        BlockState state = BlocksInit.STONE_CHICKEN_STATUE.get().defaultBlockState();
        level.setBlockAndUpdate(pos, state);
    }

    private static void playEffects(
            ServerLevel level,
            BlockPos pos
    ) {
        level.sendParticles(
                ParticleTypes.HEART,
                pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D,
                10, 0.4D, 0.4D, 0.4D, 0.05D
        );
        level.playSound(
                null, pos,
                SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL,
                0.5F, 1.6F
        );
    }

    private static void markArcComplete(TownFlagBlockEntity flag) {
        flag.setChickenBeatState(ChickenBeatState.COMPLETE);
        CompoundTag tag = Compat.getBlockStoredTagData(flag);
        flag.writeTownData(tag);
        flag.setChanged();
    }
}
