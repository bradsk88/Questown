package ca.bradj.questown.town.entity;

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

    public CompoundTag serializeNBT(TownWorkHandle workHandle) {
        CompoundTag tag = new CompoundTag();
        QTNBT.put(tag, "boards", serializeBoards(workHandle));
        QTNBT.put(tag, "requests", serializeRequests(workHandle));
        QTNBT.put(tag, "limits", serializeLimits(workHandle));
        QTNBT.putBoolean(tag, "player_has_posted", workHandle.playerHasPosted);
        return tag;
    }

    public void deserializeNBT(
            CompoundTag compound,
            TownWorkHandle workHandle
    ) {
        deserializeBoards(QTNBT.getList(compound, "boards")).forEach(
                workHandle::registerJobBoard
        );
        // In saved order; requestWork would put each on top.
        deserializeRequests(QTNBT.getList(compound, "requests")).forEach(
                workHandle::restoreRequest
        );
        workHandle.playerHasPosted = QTNBT.getBoolean(compound, "player_has_posted", false);
        // Older towns have no limits.
        if (QTNBT.contains(compound, "limits")) {
            QTNBT.getList(compound, "limits").forEach(v -> {
                CompoundTag c = (CompoundTag) v;
                workHandle.setRequestLimit(
                        WorkRequestSerializer.INSTANCE.deserialize(c.getCompound("request")),
                        c.getInt("limit")
                );
            });
        }
    }

    private static Tag serializeLimits(TownWorkHandle workHandle) {
        ListTag list = new ListTag();
        workHandle.limits.asMap().forEach((request, limit) -> {
            CompoundTag c = new CompoundTag();
            c.put("request", WorkRequestSerializer.INSTANCE.serialize(request));
            c.putInt("limit", limit);
            list.add(c);
        });
        return list;
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
