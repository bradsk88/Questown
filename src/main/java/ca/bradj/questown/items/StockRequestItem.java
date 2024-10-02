package ca.bradj.questown.items;

import ca.bradj.questown.Questown;
import ca.bradj.questown.gui.Ingredients;
import ca.bradj.questown.jobs.requests.WorkRequest;
import ca.bradj.questown.jobs.requests.WorkRequestSerializer;
import ca.bradj.questown.mc.Compat;
import ca.bradj.roomrecipes.adapter.Positions;
import ca.bradj.roomrecipes.core.Room;
import ca.bradj.roomrecipes.serialization.MCRoom;
import ca.bradj.roomrecipes.serialization.RoomSerializer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class StockRequestItem extends Item {
    public static final String ITEM_ID = "stock_request";

    public StockRequestItem() {
        super(Questown.DEFAULT_ITEM_PROPS);
    }

    public static void writeToNBT(
            CompoundTag tag,
            WorkRequest item
    ) {
        QTNBT.put(tag, "request", WorkRequestSerializer.INSTANCE.serialize(item));
    }

    public static WorkRequest getRequest(CompoundTag tag) {
        CompoundTag reqTag = QTNBT.getCompound(tag, "request");
        return WorkRequestSerializer.INSTANCE.deserialize(reqTag);
    }

    public static boolean hasRequest(CompoundTag tag) {
        if (tag == null) {
            return false;
        }
        return QTNBT.contains(tag, "request");
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

    public static boolean hasRoom(CompoundTag tag) {
        boolean x = QTNBT.contains(tag, "workspot_x");
        boolean y = QTNBT.contains(tag, "workspot_y");
        boolean z = QTNBT.contains(tag, "workspot_z");
        boolean room = QTNBT.contains(tag, "workspot_room");
        boolean room_y = QTNBT.contains(tag, "workspot_room_y");
        return x && y && z && room && room_y;
    }

    public static @Nullable MCRoom getRoom(CompoundTag tag) {
        if (!QTNBT.contains(tag, "workspot_room")) {
            return null;
        }
        if (!QTNBT.contains(tag, "workspot_room_y")) {
            return null;
        }
        Integer y = QTNBT.getInt(tag, "workspot_room_y");
        CompoundTag roomTag = QTNBT.getCompound(tag, "workspot_room");
        Room room = RoomSerializer.INSTANCE.deserializeNBT(roomTag);
        return new MCRoom(room.doorPos, room.getSpaces(), y);
    }

    public static @Nullable BlockPos getJobBlock(CompoundTag itemTag) {
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

    @Override
    public void appendHoverText(
            ItemStack p_41421_,
            @Nullable Level p_41422_,
            List<Component> tooltips,
            TooltipFlag p_41424_
    ) {
        CompoundTag tag = p_41421_.getTag();
        if (hasRequest(tag)) {
            tooltips.add(Ingredients.getName(getRequest(tag).asIngredient()));
        }
        if (p_41424_.isAdvanced()) {
            if (hasRoom(tag)) {
                tooltips.add(Compat.literal("Room Door: " + getRoom(tag).doorPos.getUIString()));
                tooltips.add(Compat.literal("Job Block: " + Positions.FromBlockPos(getJobBlock(tag)).getUIString()));
            }
        }
        super.appendHoverText(p_41421_, p_41422_, tooltips, p_41424_);
    }
}
