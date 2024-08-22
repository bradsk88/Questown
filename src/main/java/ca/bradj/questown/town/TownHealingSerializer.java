package ca.bradj.questown.town;

import net.minecraft.nbt.CompoundTag;

public class TownHealingSerializer {
    public void deserialize(
            CompoundTag tag,
            TownHealingHandle healing
    ) {
        // FIXME: Implement
    }

    public CompoundTag serialize(
            TownVillagerHandle villagers,
            Long currentTick
    ) {
        // FIXME: Implement
        return new CompoundTag();
    }
}
