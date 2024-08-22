package ca.bradj.questown.town;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Random;

public class TownHealingHandle extends HealingStore<BlockPos> {
    public static final TownHealingSerializer SERIALIZER = new TownHealingSerializer();
    private final UnsafeTown town = new UnsafeTown();

    public void initialize(TownFlagBlockEntity t) {
        town.initialize(t);
    }

    public void registerHealingBed(
            BlockPos headSpot,
            BlockPos footSpot,
            Double factor
    ) {
        super.putGroundTruth(headSpot, factor);
        super.putGroundTruth(footSpot, factor);
    }

    @Override
    public void tick() {
        super.tick();
        addEffect();
    }

    private void addEffect() {
        @NotNull ServerLevel level = town.getServerLevelUnsafe();
        @Nullable Map.Entry<BlockPos, Double> spot = getRandom(town.getServerLevelUnsafe().random::nextInt);
        if (spot == null)
            return;
        if (spot.getValue() < 1.1) {
            return;
        }
        BlockPos pos = spot.getKey().above();
        Random rand = level.random;
        level.addParticle(
                ParticleTypes.EFFECT,
                (double) pos.getX() + 0.5D,
                (double) pos.getY() + 0.5D,
                (double) pos.getZ() + 0.5D,
                (double) (rand.nextFloat() / 2.0F),
                5.0E-5D,
                (double) (rand.nextFloat() / 2.0F)
        );
    }
}
