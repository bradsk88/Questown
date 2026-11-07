package ca.bradj.questown.items;

import ca.bradj.questown.core.init.ModItemGroup;
import ca.bradj.questown.core.init.items.ItemsInit;
import ca.bradj.questown.core.network.OpenRelocationConfirmMessage;
import ca.bradj.questown.core.network.QuestownNetwork;
import ca.bradj.questown.core.network.RelocationChoiceMessage;
import ca.bradj.questown.town.entity.TownRelocation;
import ca.bradj.questown.town.entity.TownRelocation.RelocationResult;
import ca.bradj.questown.mc.Compat;
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
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/** The deed a player gets when a town shuts down. It points at the dormant flag, which still holds the town, so it can't be duplicated and losing it doesn't lose the town. */
public class RelocationDeedItem extends Item {

    public static final String ITEM_ID = "relocation_deed";

    private static final String NBT_ROOT = "questown_relocation";
    private static final String NBT_TOWN_UUID = "town_uuid";
    private static final String NBT_FLAG_POS = "flag_pos";
    private static final String NBT_DIM = "dimension";

    public RelocationDeedItem() {
        super(new Item.Properties().stacksTo(1));
    }

    /** A deed pointing at a dormant flag: the town, its position and its dimension. */
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
        {
            CompoundTag t = Compat.itemDataTag(stack);
            t.put(NBT_ROOT, ref);
            Compat.setItemDataTag(stack, t);
        }
        return stack;
    }

    /** Places the town at the clicked face. If some of the town would be too far from the new flag, ask first. The deed is used up only when the move succeeds. */
    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (!(ctx.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.sidedSuccess(ctx.getLevel().isClientSide());
        }
        BlockPos targetPos = ctx.getClickedPos().relative(ctx.getClickedFace());
        ItemStack deed = ctx.getItemInHand();

        int farFixtures = TownRelocation.farFixturesFor(level, deed, targetPos).size();
        if (farFixtures > 0 && ctx.getPlayer() instanceof ServerPlayer player) {
            QuestownNetwork.sendToPlayer(
                    player,
                    new OpenRelocationConfirmMessage(targetPos, farFixtures)
            );
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

    /** Clears the deed from the player's hand. Creative would otherwise give it back, and a second placement would make a second town. */
    public static void consumeFrom(
            Player player,
            InteractionHand hand
    ) {
        player.setItemInHand(hand, ItemStack.EMPTY);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            @org.jetbrains.annotations.Nullable net.minecraft.world.level.Level ctx,
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
        CompoundTag data = Compat.itemDataTag(stack);
        if (data.isEmpty() || !data.contains(NBT_ROOT)) {
            return null;
        }
        return data.getCompound(NBT_ROOT);
    }
}
