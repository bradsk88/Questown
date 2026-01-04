package ca.bradj.questown.town;

import ca.bradj.questown.town.entity.TownVillagerHandle;
import net.minecraft.nbt.CompoundTag;

public class TownHealingSerializer {
    public void deserialize(
            CompoundTag tag,
            TownHealingHandle healing
    ) {
        // TODO: Implement
    }

    public CompoundTag serialize(
            TownVillagerHandle villagers,
            Long currentTick
    ) {
        // TODO: Implement
        return new CompoundTag();
    }
}
