package ca.bradj.questown.jobs.requests;
import ca.bradj.questown.loader.Loader;

import ca.bradj.questown.items.QTNBT;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.tags.TagKey;

public class WorkRequestSerializer {
    public static final WorkRequestSerializer INSTANCE = new WorkRequestSerializer();

    public Tag serialize(WorkRequest v) {
        CompoundTag t = new CompoundTag();
        if (v.tag != null) {
            QTNBT.put(t, "tag", v.tag.location());
        }
        if (v.item != null) {
            QTNBT.put(t, "item", Loader.itemIdOf(v.item));
        }
        return t;
    }

    public WorkRequest deserialize(CompoundTag t) {
        if (QTNBT.contains(t, "tag")) {
            return WorkRequest.of(new TagKey<>(Registries.ITEM, QTNBT.getResourceLocation(t, "tag")));
        }
        if (QTNBT.contains(t, "item")) {
            return WorkRequest.of(Loader.itemFromId(QTNBT.getResourceLocation(t, "item")));
        }
        throw new IllegalStateException("Missing both tag and item for WorkRequest");
    }
}
