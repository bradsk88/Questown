package ca.bradj.questown.town;

import ca.bradj.questown.items.QTNBT;
import ca.bradj.questown.jobs.requests.WorkRequest;
import ca.bradj.questown.jobs.requests.WorkRequestSerializer;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class TownWorkHandleSerializer {
    public static final TownWorkHandleSerializer INSTANCE = new TownWorkHandleSerializer();

    public Tag serializeNBT(TownWorkHandle workHandle) {
        CompoundTag tag = new CompoundTag();
        QTNBT.put(tag, "boards", serializeBoards(workHandle));
        QTNBT.put(tag, "requests", serializeRequests(workHandle));
        return tag;
    }

    public void deserializeNBT(
            CompoundTag compound,
            TownWorkHandle workHandle
    ) {
        deserializeBoards(QTNBT.getList(compound, "boards")).forEach(
                workHandle::registerJobBoard
        );
        deserializeRequests(QTNBT.getList(compound, "requests")).forEach(
                workHandle::requestWork
        );
    }

    @NotNull
    private static ListTag serializeBoards(TownWorkHandle workHandle) {
        ListTag list = new ListTag();
        workHandle.jobBoards.forEach(v -> {
            CompoundTag bT = new CompoundTag();
            bT.putInt("x", v.getX());
            bT.putInt("y", v.getY());
            bT.putInt("z", v.getZ());
            list.add(bT);
        });
        return list;
    }

    private Iterable<BlockPos> deserializeBoards(ListTag boards) {
        return boards.stream().map(v -> {
            CompoundTag vc = (CompoundTag) v;
            return new BlockPos(vc.getInt("x"), vc.getInt("y"), vc.getInt("z"));
        }).toList();
    }

    private Tag serializeRequests(TownWorkHandle workHandle) {
        ListTag list = new ListTag();
        workHandle.requestedResults.forEach(v -> {
            Tag tag = WorkRequestSerializer.INSTANCE.serialize(v);
            list.add(tag);
        });
        return list;
    }

    private Collection<WorkRequest> deserializeRequests(ListTag requests) {
        ImmutableList.Builder<WorkRequest> b = ImmutableList.builder();
        requests.forEach(v -> b.add(WorkRequestSerializer.INSTANCE.deserialize((CompoundTag) v)));
        return b.build();
    }

}
