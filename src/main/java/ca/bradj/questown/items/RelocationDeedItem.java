package ca.bradj.questown.items;

import ca.bradj.questown.core.init.ModItemGroup;
import ca.bradj.questown.core.init.items.ItemsInit;
import ca.bradj.questown.core.network.OpenRelocationConfirmMessage;
import ca.bradj.questown.core.network.QuestownNetwork;
import ca.bradj.questown.core.network.RelocationChoiceMessage;
import ca.bradj.questown.town.entity.TownRelocation;
import ca.bradj.questown.town.entity.TownRelocation.RelocationResult;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.List;
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

    /**
     * Placing the deed relocates the town: the new flag goes at the block face the player clicked
     * (one block off the clicked surface, the same idiom as placing a block). When some of the town's
     * fixtures would land outside the new flag's tick radius, open the confirmation screen instead and
     * let the player decide (bring / leave / cancel) — {@link RelocationChoiceMessage} finishes the
     * placement. Otherwise place straight away, carrying everything. Either way the deed is consumed
     * only on a successful placement, so a failed relocation never strands the town.
     */
    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (!(ctx.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.sidedSuccess(ctx.getLevel().isClientSide());
        }
        BlockPos targetPos = ctx.getClickedPos().relative(ctx.getClickedFace());
        ItemStack deed = ctx.getItemInHand();

        int farFixtures = TownRelocation.farFixturesFor(level, deed, targetPos).size();
        if (farFixtures > 0 && ctx.getPlayer() instanceof ServerPlayer player) {
            QuestownNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new OpenRelocationConfirmMessage(targetPos, farFixtures)
            );
            // Deed stays in hand; RelocationChoiceMessage will place + consume it once they choose.
            return InteractionResult.SUCCESS;
        }

        RelocationResult result = TownRelocation.place(level, deed, targetPos);
        if (result != RelocationResult.OK) {
            if (ctx.getPlayer() != null) {
                ctx.getPlayer().displayClientMessage(messageFor(result), true);
            }
            return InteractionResult.FAIL;
        }
        consumeDeed(ctx);
        return InteractionResult.CONSUME;
    }

    private static void consumeDeed(UseOnContext ctx) {
        if (ctx.getPlayer() == null) {
            ctx.getItemInHand().shrink(1);
            return;
        }
        consumeFrom(ctx.getPlayer(), ctx.getHand());
    }

    /**
     * Take a placed deed out of the player's hand. Deeds are unstackable one-shots, so the slot is
     * cleared explicitly rather than shrunk: creative restores the count of a used stack, and a deed
     * that survives its own placement references a flag that no longer exists — place it again and
     * the town has two.
     */
    public static void consumeFrom(
            Player player,
            InteractionHand hand
    ) {
        player.setItemInHand(hand, ItemStack.EMPTY);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            @Nullable Level level,
            List<Component> tooltip,
            TooltipFlag flag
    ) {
        tooltip.add(Component.translatable("item.questown.relocation_deed.tooltip"));
    }

    public static Component messageFor(RelocationResult result) {
        return Component.translatable("message.questown.relocation_deed." + result.name().toLowerCase());
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
