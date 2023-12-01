package ca.bradj.questown.jobs.requests;

import ca.bradj.questown.items.QTNBT;
import net.minecraft.core.Registry;
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
            QTNBT.put(t, "item", v.item.getRegistryName());
        }
        return t;
    }

    public WorkRequest deserialize(CompoundTag t) {
        if (t.contains("tag")) {
            return WorkRequest.of(new TagKey<>(Registry.ITEM_REGISTRY, QTNBT.getResourceLocation(t, "tag")));
        }
        if (t.contains("item")) {
            return WorkRequest.of(new TagKey<>(Registry.ITEM_REGISTRY, QTNBT.getResourceLocation(t, "item")));
        }
        throw new IllegalStateException("Missing both tag and item for WorkRequest");
    }
}
