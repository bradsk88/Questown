package ca.bradj.questown.items;

import ca.bradj.questown.core.init.ModItemGroup;
import ca.bradj.questown.core.init.items.ItemsInit;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The {@code relocation_deed} a player receives when a {@link ca.bradj.questown.blocks.FlagPhase#DORMANT
 * town shutdown} completes (ADR-0009, #199). It carries <em>only a reference</em> to the dormant
 * flag — the town UUID, the original flag {@link BlockPos}, and the origin dimension — never a town
 * snapshot. Because the authoritative data has exactly one home (the dormant flag), the deed is
 * dupe-proof and losing it never strands the town.
 *
 * <p>Unstackable and non-craftable: the only source is the shutdown ritual or a dormant-flag re-issue.
 */
public class RelocationDeedItem extends Item {

    public static final String ITEM_ID = "relocation_deed";

    private static final String NBT_ROOT = "questown_relocation";
    private static final String NBT_TOWN_UUID = "town_uuid";
    private static final String NBT_FLAG_POS = "flag_pos";
    private static final String NBT_DIM = "dimension";

    public RelocationDeedItem() {
        // Fresh Properties (not the shared DEFAULT_ITEM_PROPS) so stacksTo(1) doesn't mutate it.
        super(new Item.Properties().tab(ModItemGroup.QUESTOWN_GROUP).stacksTo(1));
    }

    /**
     * Build a deed referencing a dormant flag. The reference is everything Phase 3 placement needs
     * to load, copy, and destroy the original: which town, where, and in which dimension.
     */
    public static ItemStack forReference(
            UUID townUuid,
            BlockPos flagPos,
            ResourceLocation dimension
    ) {
        ItemStack stack = new ItemStack(ItemsInit.RELOCATION_DEED.get());
        CompoundTag ref = new CompoundTag();
        ref.putUUID(NBT_TOWN_UUID, townUuid);
        ref.putLong(NBT_FLAG_POS, flagPos.asLong());
        ref.putString(NBT_DIM, dimension.toString());
        stack.getOrCreateTag().put(NBT_ROOT, ref);
        return stack;
    }

    public static boolean isDeed(ItemStack stack) {
        return stack.getItem() instanceof RelocationDeedItem;
    }

    public static boolean hasReference(ItemStack stack) {
        return reference(stack) != null;
    }

    public static @Nullable BlockPos getFlagPos(ItemStack stack) {
        CompoundTag ref = reference(stack);
        return ref == null ? null : BlockPos.of(ref.getLong(NBT_FLAG_POS));
    }

    public static @Nullable UUID getTownUuid(ItemStack stack) {
        CompoundTag ref = reference(stack);
        if (ref == null || !ref.hasUUID(NBT_TOWN_UUID)) {
            return null;
        }
        return ref.getUUID(NBT_TOWN_UUID);
    }

    public static @Nullable ResourceLocation getDimension(ItemStack stack) {
        CompoundTag ref = reference(stack);
        return ref == null ? null : ResourceLocation.tryParse(ref.getString(NBT_DIM));
    }

    private static @Nullable CompoundTag reference(ItemStack stack) {
        if (!isDeed(stack)) {
            return null;
        }
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(NBT_ROOT)) {
            return null;
        }
        return tag.getCompound(NBT_ROOT);
    }
}
