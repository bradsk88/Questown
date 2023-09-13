package ca.bradj.questown.town.rooms;

import ca.bradj.questown.Questown;
import ca.bradj.questown.town.TownFlagBlockEntity;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.roomrecipes.recipes.ActiveRecipes;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.Map;

public class TownRoomsMapSerializer {

    public static final TownRoomsMapSerializer INSTANCE = new TownRoomsMapSerializer();
    private static final String NBT_REGISTERED_DOORS = String.format("%s_registered_doors", Questown.MODID);
    private static final String NBT_REGISTERED_FENCE_GATES = String.format("%s_registered_fence_gates", Questown.MODID);
    public static final String NBT_ACTIVE_RECIPES = String.format("%s_active_recipes", Questown.MODID);
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
        Map<Integer, ActiveRecipes<MCRoom, RoomRecipeMatch>> activeRecipes = ImmutableMap.of();
        if (tag.contains(NBT_ACTIVE_RECIPES)) {
//            CompoundTag data = tag.getCompound(NBT_ACTIVE_RECIPES); TODO: Bring back? (Cost to re-compute is low, I think)
//            ActiveRecipes<ResourceLocation> ars = ActiveRecipesSerializer.INSTANCE.deserializeNBT(data);
//            this.roomsMap.initialize(this, ImmutableMap.of(0, ars)); // TODO: Support more levels
        }
        roomsMap.initialize(owner, activeRecipes, doorsB.build(), gatesB.build());
    }

    public Tag serializeNBT(TownRoomsMap roomsMap) {
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
        return tag;
    }
}
