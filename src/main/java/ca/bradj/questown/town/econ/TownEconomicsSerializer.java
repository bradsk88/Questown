package ca.bradj.questown.town.econ;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.UUID;

public class TownEconomicsSerializer {
    public static final TownEconomicsSerializer INSTANCE = new TownEconomicsSerializer();
    public static final String NBT_REQUEST = "request";
    public static final String NBT_VILLAGER = "villager";
    public static final String NBT_TICK = "tick";
    public static final String NBT_NEEDS = "needs";
    public static final String NBT_ROOMS = "rooms";

    public CompoundTag serialize(
            NoMCEconomics econ
    ) {
        CompoundTag tag = new CompoundTag();
        ListTag needs = new ListTag();
        for (NoMCEconomics.UnmetNeed unmetNeed : econ.unmetNeedsRecord) {
            CompoundTag t = new CompoundTag();
            t.putString(NBT_REQUEST, unmetNeed.request());
            t.putUUID(NBT_VILLAGER, unmetNeed.villager());
            t.putLong(NBT_TICK, unmetNeed.tick());
            needs.add(t);
        }
        tag.put(NBT_NEEDS, needs);

        ListTag rooms = new ListTag();
        for (NoMCEconomics.UnmetNeed unmetNeed : econ.unmetRoomsRecord) {
            CompoundTag t = new CompoundTag();
            t.putString(NBT_REQUEST, unmetNeed.request());
            t.putUUID(NBT_VILLAGER, unmetNeed.villager());
            t.putLong(NBT_TICK, unmetNeed.tick());
            rooms.add(t);
        }
        tag.put(NBT_ROOMS, rooms);
        return tag;
    }

    public void deserialize(
            NoMCEconomics econ,
            CompoundTag tag
    ) {
        ListTag needs = tag.getList(NBT_NEEDS, ListTag.TAG_COMPOUND);
        needs.forEach(t -> {
            CompoundTag tt = (CompoundTag) t;
            String req = tt.getString(NBT_REQUEST);
            UUID vil = tt.getUUID(NBT_VILLAGER);
            long tic = tt.getLong(NBT_TICK);
            econ.registerUnmetNeed(tic, vil, req);
        });
        ListTag rooms = tag.getList(NBT_ROOMS, ListTag.TAG_COMPOUND);
        rooms.forEach(t -> {
            CompoundTag tt = (CompoundTag) t;
            String req = tt.getString(NBT_REQUEST);
            UUID vil = tt.getUUID(NBT_VILLAGER);
            long tic = tt.getLong(NBT_TICK);
            econ.registerUnmetRoom(tic, vil, req);
        });
        econ.needAggregate = true;
    }
}
