package ca.bradj.questown.town;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public class TownHealingSerializer {
    public void deserialize(
            CompoundTag tag,
            TownHealingHandle healing
    ) {
        // FIXME: Implement
    }

    public Tag serialize(
            TownVillagerHandle villagers,
            Long currentTick
    ) {
        // FIXME: Implement
        return new CompoundTag();
    }
}
