package ca.bradj.questown.town;

import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.Collection;

public class WelcomeMatsSerializer {
    public static final WelcomeMatsSerializer INSTANCE = new WelcomeMatsSerializer();

    public CompoundTag serializeNBT(ImmutableList<BlockPos> welcomeMats) {
        ListTag tag = new ListTag();
        for (BlockPos m : welcomeMats) {
            CompoundTag t = new CompoundTag();
            t.putInt("x", m.getX());
            t.putInt("y", m.getY());
            t.putInt("z", m.getZ());
            tag.add(t);
        }
        CompoundTag out = new CompoundTag();
        out.put("mats", tag);
        return out;
    }

    public Collection<BlockPos> deserializeNBT(
            CompoundTag data
    ) {
        ImmutableList.Builder<BlockPos> b = ImmutableList.builder();
        for (Tag t : data.getList("mats", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            b.add(new BlockPos(c.getInt("x"), c.getInt("y"), c.getInt("z")));
        }
        return b.build();
    }
}
