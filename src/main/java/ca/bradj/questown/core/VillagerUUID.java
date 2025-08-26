package ca.bradj.questown.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

public final class VillagerUUID {
    private final UUID uuid;

    private VillagerUUID(UUID uuid) {
        this.uuid = uuid;
    }

    /**
     * @deprecated Avoid use at all costs
     */
    @Deprecated(forRemoval = true, since = "0.0.9")
    public static @Nullable VillagerUUID from(@Nullable UUID randomVillager) {
        if (randomVillager == null) {
            return null;
        }
        return new VillagerUUID(randomVillager);
    }

    public static VillagerUUID fromNBT(
            CompoundTag nbt,
            String key
    ) {
        return new VillagerUUID(nbt.getUUID(key));
    }

    public static VillagerUUID random() {
        return new VillagerUUID(UUID.randomUUID());
    }

    /**
     * @deprecated Update callers to pass VillagerUUID around.
     */
    @Deprecated(forRemoval = true, since = "0.0.9")
    public static @Nullable UUID get(@Nullable VillagerUUID v) {
        if (v == null) {
            return null;
        }
        return v.uuid;
    }

    public static String getStringUUID(VillagerUUID villagerUUID) {
        if (villagerUUID == null) {
            return "";
        }
        return villagerUUID.uuid.toString();
    }

    public static VillagerUUID fromNetwork(FriendlyByteBuf buf) {
        String s = buf.readUtf();
        if (s.isEmpty()) {
            return null;
        }
        return new VillagerUUID(UUID.fromString(s));
    }

    public static void toNetwork(FriendlyByteBuf buf, VillagerUUID villagerUUID) {
        String v = "";
        if (villagerUUID != null) {
            v = VillagerUUID.getStringUUID(villagerUUID);
        }
        buf.writeUtf(v);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (VillagerUUID) obj;
        return Objects.equals(this.uuid, that.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid);
    }

    @Override
    public String toString() {
        return "VillagerUUID[" + uuid + ']';
    }

    public void writeToNBT(
            CompoundTag tag,
            String value
    ) {
        tag.putUUID(value, uuid);
    }

    public boolean matches(UUID owner) {
        return uuid.equals(owner);
    }
}
