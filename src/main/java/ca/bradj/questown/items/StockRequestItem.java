package ca.bradj.questown.items;

import ca.bradj.questown.Questown;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.roomrecipes.core.Room;
import ca.bradj.roomrecipes.serialization.MCRoom;
import ca.bradj.roomrecipes.serialization.RoomSerializer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

public class StockRequestItem extends Item {
    public static final String ITEM_ID = "stock_request";

    public StockRequestItem() {
        super(Questown.DEFAULT_ITEM_PROPS);
    }

    public static void writeToNBT(
            CompoundTag tag,
            MCRoom room,
            BlockPos p
    ) {
        QTNBT.putInt(tag, "workspot_x", p.getX());
        QTNBT.putInt(tag, "workspot_y", p.getY());
        QTNBT.putInt(tag, "workspot_z", p.getZ());
        QTNBT.put(tag, "workspot_room", RoomSerializer.INSTANCE.serializeNBT(room));
        QTNBT.putInt(tag, "workspot_room_y", room.yCoord);
    }

    public static boolean hasNBT() {
        // TODO: Implement
        return false;
    }

    public static @Nullable MCRoom getRoom(CompoundTag tag) {
        if (!tag.contains("workspot_room")) {
            return null;
        }
        if (!tag.contains("workspot_room_y")) {
            return null;
        }
        Integer y = QTNBT.getInt(tag, "workspot_room_y");
        CompoundTag roomTag = QTNBT.getCompound(tag, "workspot_room");
        Room room = RoomSerializer.INSTANCE.deserializeNBT(roomTag);
        return new MCRoom(room.doorPos, room.getSpaces(), y);
    }

    public static @Nullable BlockPos getJobBlock(MCHeldItem item) {
        CompoundTag tag = item.getItemNBTData();
        CompoundTag itemTag = tag.getCompound("item");
        Integer x = null, y = null, z = null;
        if (QTNBT.contains(itemTag, "workspot_x")) {
            x = QTNBT.getInt(itemTag, "workspot_x");
        }
        if (QTNBT.contains(itemTag, "workspot_y")) {
            y = QTNBT.getInt(itemTag, "workspot_y");
        }
        if (QTNBT.contains(itemTag, "workspot_z")) {
            z = QTNBT.getInt(itemTag, "workspot_z");
        }
        if (x == null || y == null || z == null) {
            return null;
        }
        return new BlockPos(x, y, z);
    }

    // TODO: Hover text
}
