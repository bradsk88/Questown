package ca.bradj.questown.town;

import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.items.QTNBT;
import com.google.common.collect.ImmutableList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.List;

public class TownKnowledgeStoreSerializer {
    public static final TownKnowledgeStoreSerializer INSTANCE = new TownKnowledgeStoreSerializer();
    private static final String NBT_GATHER_RESULTS = "gather_results";

    public void deserializeNBT(
            CompoundTag compound,
            TownKnowledgeStore knowledgeHandle
    ) {
        ListTag gatherResults = QTNBT.getList(compound, NBT_GATHER_RESULTS);
        List<MCHeldItem> knownLoot = gatherResults.stream().map(v -> MCHeldItem.deserialize((CompoundTag) v)).toList();
        knowledgeHandle.registerFoundLoots(ImmutableList.copyOf(knownLoot));
    }

    public Tag serializeNBT(TownKnowledgeStore knowledgeHandle) {
        CompoundTag t = new CompoundTag();
        ListTag gatherResults = new ListTag();
        knowledgeHandle.getAllKnownGatherResults().forEach(
                v -> gatherResults.add(v.serializeNBT())
        );
        QTNBT.put(t, NBT_GATHER_RESULTS, gatherResults);
        return t;
    }
}
