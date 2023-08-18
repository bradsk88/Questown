package ca.bradj.questown.town;

import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.Collection;

public class WelcomeMatsSerializer {
    public static final WelcomeMatsSerializer INSTANCE = new WelcomeMatsSerializer();

    public Tag serializeNBT(ImmutableList<BlockPos> welcomeMats) {
        ListTag tag = new ListTag();
        for (BlockPos m : welcomeMats) {
            CompoundTag t = new CompoundTag();
            t.putInt("x", m.getX());
            t.putInt("y", m.getY());
            t.putInt("z", m.getZ());
            tag.add(t);
        }
        return tag;
    }

    public Collection<BlockPos> deserializeNBT(
            ListTag data
    ) {
        ImmutableList.Builder<BlockPos> b = ImmutableList.builder();
        for (Tag t : data) {
            CompoundTag c = (CompoundTag) t;
            b.add(new BlockPos(c.getInt("x"), c.getInt("y"), c.getInt("z")));
        }
        return b.build();
    }
}
