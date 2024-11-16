package ca.bradj.questown.items;

import ca.bradj.questown.Questown;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class QTNBT {
    private static String keyify(String key) {
        return String.format("%s_%s", Questown.MODID, key);
    }

    public static void putString(
            ItemStack map,
            String key,
            String value
    ) {
        map.getOrCreateTag().putString(keyify(key), value);
    }

    public static @Nullable String getString(
            CompoundTag tag,
            String prefix
    ) {
        if (contains(tag, prefix)) {
            return tag.getString(keyify(prefix));
        }
        return null;
    }

    public static void put(
            CompoundTag target,
            String key,
            Tag tag
    ) {
        target.put(keyify(key), tag);
    }

    public static <X> void put(
            Map<String, X> target,
            String key,
            X tag
    ) {
        target.put(keyify(key), tag);
    }

    public static ListTag getList(
            CompoundTag compound,
            String key
    ) {
        return compound.getList(keyify(key), Tag.TAG_COMPOUND);
    }

    public static void put(
            CompoundTag t,
            String key,
            ResourceLocation location
    ) {
        t.putString(keyify(key), location.toString());
    }


    public static void putLong(
            CompoundTag t,
            String key,
            long val
    ) {
        t.putLong(keyify(key), val);
    }


    public static void putInt(
            CompoundTag t,
            String key,
            int val
    ) {
        t.putInt(keyify(key), val);
    }

    public static ResourceLocation getResourceLocation(
            CompoundTag tag,
            String key
    ) {
        return new ResourceLocation(getString(tag, key));
    }

    public static boolean contains(
            CompoundTag t,
            String item
    ) {
        return t.contains(keyify(item));
    }

    public static CompoundTag getCompound(
            CompoundTag tag,
            String knowledge
    ) {
        return tag.getCompound(keyify(knowledge));
    }

    public static long getLong(
            CompoundTag t,
            String key
    ) {
        return t.getLong(keyify(key));
    }

    public static Integer getInt(
            CompoundTag itemTag,
            String key
    ) {
        return itemTag.getInt(keyify(key));
    }
}
