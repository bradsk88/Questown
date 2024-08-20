package ca.bradj.questown.town;

import net.minecraft.core.BlockPos;

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
}
