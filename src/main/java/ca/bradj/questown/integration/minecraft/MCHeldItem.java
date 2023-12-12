package ca.bradj.questown.integration.minecraft;

import ca.bradj.questown.items.QTNBT;
import ca.bradj.questown.jobs.HeldItem;
import ca.bradj.questown.jobs.gatherer.GathererTools;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class MCHeldItem implements HeldItem<MCHeldItem, MCTownItem> {

    private final MCTownItem delegate;
    private boolean locked = false;
    private final String prefix;
    private final String biome;

    private MCHeldItem(
            MCTownItem item,
            boolean locked,
            @Nullable String prefix,
            @Nullable String biome
    ) {
        this.delegate = item;
        this.locked = locked;
        this.prefix = prefix;
        this.biome = biome;
    }

    public static MCHeldItem fromLootTable(MCTownItem item, GathererTools.LootTablePrefix lootTablePrefix, ResourceLocation biome) {
        return new MCHeldItem(item, false, lootTablePrefix.value(), biome.toString());
    }

    public static MCHeldItem fromTag(CompoundTag tag) {
        CompoundTag nbt = tag.getCompound("item");
        ItemStack stack = ItemStack.of(nbt);
        MCTownItem ti = new MCTownItem(stack.getItem(), stack.getCount(), nbt);
        boolean loqued = tag.getBoolean("locked");
        String prefix = QTNBT.getString(tag, "prefix");
        String biome = QTNBT.getString(tag, "biome");
        return new MCHeldItem(ti, loqued, prefix, biome);
    }

    public static MCHeldItem fromMCItemStack(ItemStack item) {
        return new MCHeldItem(
                MCTownItem.fromMCItemStack(item),
                false,
                QTNBT.getString(item.getOrCreateTag(), "prefix"),
                QTNBT.getString(item.getOrCreateTag(), "biome")
        );
    }

    public static MCHeldItem fromTown(MCTownItem mcTownItem) {
        return new MCHeldItem(mcTownItem, false, null, null);
    }

    public static MCHeldItem fromTown(ItemStack itemstack) {
        return fromTown(MCTownItem.fromMCItemStack(itemstack));
    }

    @Override
    public boolean isLocked() {
        return locked;
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    @Override
    public boolean isFood() {
        return delegate.isFood();
    }

    @Override
    public MCHeldItem shrink() {
        return new MCHeldItem(delegate.shrink(), locked, prefix, biome);
    }

    public static MCHeldItem Air() {
        return new MCHeldItem(MCTownItem.Air(), false, null, null);
    }

    public MCTownItem get() {
        return delegate;
    }

    @Override
    public @Nullable String acquiredViaLootTablePrefix() {
        return prefix;
    }

    @Override
    public @Nullable String foundInBiome() {
        return biome;
    }

    @Override
    public MCHeldItem locked() {
        return new MCHeldItem(delegate, true, prefix, biome);
    }

    @Override
    public MCHeldItem unlocked() {
        return new MCHeldItem(delegate, false, prefix, biome);
    }

    public Tag serializeNBT() {
        CompoundTag tag = delegate.serializeNBT();
        tag.putBoolean("locked", locked);
        if (biome != null) {
            tag.putString("biome", biome);
        }
        if (prefix != null) {
            tag.putString("prefix", prefix);
        }
        return tag;
    }

    public static MCHeldItem deserialize(CompoundTag v) {
        ItemStack del = ItemStack.of(v.getCompound("item"));
        boolean locked = false;
        if (v.contains("locked")) {
            locked = v.getBoolean("locked");
        }
        String biome = null;
        if (v.contains("biome")) {
            biome = v.getString("biome");
        }
        String prefix = null;
        if (v.contains("prefix")) {
            prefix = v.getString("prefix");
        }
        return new MCHeldItem(MCTownItem.fromMCItemStack(del), locked, prefix, biome);
    }

    public MCTownItem toItem() {
        return delegate;
    }

    @Override
    public String toString() {
        return "MCHeldItem{" +
                "delegate=" + delegate +
                ", locked=" + locked +
                ", prefix=" + prefix +
                ", biome=" + biome +
                '}';
    }

    public String toShortString() {
        String itemName = ForgeRegistries.ITEMS.getKey(delegate.get()).toString();
        return String.format("{item=%s, prefix=%s, biome=%s}", itemName, prefix, biome);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MCHeldItem that = (MCHeldItem) o;
        return locked == that.locked && Objects.equals(delegate, that.delegate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(delegate, locked);
    }
}
