package ca.bradj.questown.town.rooms;

import ca.bradj.questown.Questown;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public class TownRoomsMapSerializer {

    public static final TownRoomsMapSerializer INSTANCE = new TownRoomsMapSerializer();
    private static final String NBT_REGISTERED_DOORS = String.format("%s_registered_doors", Questown.MODID);
    private static final String NBT_REGISTERED_FENCE_GATES = String.format("%s_registered_fence_gates", Questown.MODID);
    private static final String NBT_DOORS_WITH_ACTIVE_RECIPES = String.format("%s_doors_with_active_recipes", Questown.MODID);
    private static final String NBT_POS_X = "position_x";
    private static final String NBT_POS_Y = "position_y";
    private static final String NBT_POS_Z = "position_z";

    public void deserialize(
            CompoundTag tag,
            TownFlagBlockEntity owner,
            TownRoomsMap roomsMap
    ) {
        ImmutableList.Builder<TownPosition> doorsB = ImmutableList.builder();
        if (tag.contains(NBT_REGISTERED_DOORS)) {
            ListTag doors = tag.getList(NBT_REGISTERED_DOORS, Tag.TAG_COMPOUND);
            for (Tag t : doors) {
                CompoundTag ct = (CompoundTag) t;
                int x = ct.getInt(NBT_POS_X);
                int y = ct.getInt(NBT_POS_Y);
                int z = ct.getInt(NBT_POS_Z);
                doorsB.add(new TownPosition(x, z, y));
            }
        }
        ImmutableList.Builder<TownPosition> gatesB = ImmutableList.builder();
        if (tag.contains(NBT_REGISTERED_FENCE_GATES)) {
            ListTag gates = tag.getList(NBT_REGISTERED_FENCE_GATES, Tag.TAG_COMPOUND);
            for (Tag t : gates) {
                CompoundTag ct = (CompoundTag) t;
                int x = ct.getInt(NBT_POS_X);
                int y = ct.getInt(NBT_POS_Y);
                int z = ct.getInt(NBT_POS_Z);
                gatesB.add(new TownPosition(x, z, y));
            }
        }
        ImmutableList.Builder<TownPosition> doorsWithRecipes = ImmutableList.builder();
        if (tag.contains(NBT_DOORS_WITH_ACTIVE_RECIPES)) {
            ListTag gates = tag.getList(NBT_DOORS_WITH_ACTIVE_RECIPES, Tag.TAG_COMPOUND);
            for (Tag t : gates) {
                CompoundTag ct = (CompoundTag) t;
                int x = ct.getInt(NBT_POS_X);
                int y = ct.getInt(NBT_POS_Y);
                int z = ct.getInt(NBT_POS_Z);
                doorsWithRecipes.add(Compat.townPos(owner.getTownFlagBasePos(), new BlockPos(x, y, z)));
            }
        }
        roomsMap.initialize(owner, doorsB.build(), gatesB.build(), doorsWithRecipes.build());
    }

    /**
     * Re-anchor registered doors and fence gates to a flag at a new Y, preserving their absolute
     * world Y (ADR-0009 flag relocation, #199). The stored {@code position_y} is a flag-relative
     * {@code scanLevel}; rewrite it so {@code oldFlagY + scanLevel} (the absolute Y) is unchanged
     * under {@code newFlagY}. X/Z are absolute and untouched. Doors-with-active-recipes are stored
     * as absolute {@link BlockPos} and so need no rebase.
     */
    public void rebaseFixtureY(
            CompoundTag roomsTag,
            int oldFlagY,
            int newFlagY
    ) {
        rebaseScanLevels(roomsTag, NBT_REGISTERED_DOORS, oldFlagY, newFlagY);
        rebaseScanLevels(roomsTag, NBT_REGISTERED_FENCE_GATES, oldFlagY, newFlagY);
    }

    private static void rebaseScanLevels(
            CompoundTag roomsTag,
            String key,
            int oldFlagY,
            int newFlagY
    ) {
        if (!roomsTag.contains(key)) {
            return;
        }
        ListTag entries = roomsTag.getList(key, Tag.TAG_COMPOUND);
        for (Tag t : entries) {
            CompoundTag ct = (CompoundTag) t;
            int absoluteY = oldFlagY + ct.getInt(NBT_POS_Y);
            ct.putInt(NBT_POS_Y, absoluteY - newFlagY);
        }
    }

    public CompoundTag serializeNBT(TownRoomsMap roomsMap) {
        CompoundTag tag = new CompoundTag();
        ListTag doors = new ListTag();
        for (TownPosition bp : roomsMap.getRegisteredDoors()) {
            CompoundTag bpt = new CompoundTag();
            bpt.putInt(NBT_POS_X, bp.x);
            bpt.putInt(NBT_POS_Y, bp.scanLevel);
            bpt.putInt(NBT_POS_Z, bp.z);
            doors.add(bpt);
        }
        tag.put(NBT_REGISTERED_DOORS, doors);

        ListTag gates = new ListTag();
        for (TownPosition bp : roomsMap.getRegisteredGates()) {
            CompoundTag bpt = new CompoundTag();
            bpt.putInt(NBT_POS_X, bp.x);
            bpt.putInt(NBT_POS_Y, bp.scanLevel);
            bpt.putInt(NBT_POS_Z, bp.z);
            gates.add(bpt);
        }
        tag.put(NBT_REGISTERED_FENCE_GATES, gates);

        ListTag doorsWithActive = new ListTag();
        for (BlockPos bp : roomsMap.getAllActiveRecipeDoors()) {
            CompoundTag t = new CompoundTag();
            t.putInt(NBT_POS_X, bp.getX());
            t.putInt(NBT_POS_Y, bp.getY());
            t.putInt(NBT_POS_Z, bp.getZ());
            doorsWithActive.add(t);
        }
        tag.put(NBT_DOORS_WITH_ACTIVE_RECIPES, doorsWithActive);

        return tag;
    }
}
