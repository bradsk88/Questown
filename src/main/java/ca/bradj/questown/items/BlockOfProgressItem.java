package ca.bradj.questown.items;

import net.minecraft.world.level.Level;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.InteractionResult;
import net.minecraft.server.level.ServerPlayer;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import ca.bradj.questown.blocks.BlockOfProgress;
import ca.bradj.questown.core.init.BlocksInit;
import ca.bradj.questown.mc.Compat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * The Block of Progress item. A "gold" (blessed) stack calls itself a Gold Block of Progress and
 * carries a guaranteed root job (see {@link BlockOfProgress#bless(ItemStack, ca.bradj.questown.jobs.JobID)}),
 * shown as secondary text: "Guaranteed to change job to <root>".
 */
public class BlockOfProgressItem extends BlockItem {

    public BlockOfProgressItem(Properties props) {
        super(BlocksInit.BLOCK_OF_PROGRESS.get(), props);
    }

    /**
     * A Block of Progress is not a building block: used on the flag it goes back into it (a sneak
     * skips the flag's own click handler), and anywhere else it does nothing. Dev playtest
     * 2026-10-30: "shift-rightclick with BOP in hand placed it on the ground. Breaking it destroyed
     * it permanently."
     */
    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        if (level.getBlockEntity(ctx.getClickedPos()) instanceof TownFlagBlockEntity flag) {
            if (ctx.getPlayer() instanceof ServerPlayer sp) {
                flag.returnBopFromPlayer(sp, ctx.getItemInHand());
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        return InteractionResult.PASS;
    }

    @Override
    public Component getName(ItemStack stack) {
        if (BlockOfProgress.isBlessed(stack)) {
            return Compat.translatable("block.questown.block_of_progress.blessed");
        }
        return super.getName(stack);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            net.minecraft.world.item.Item.TooltipContext ctx,
            List<Component> tooltip,
            TooltipFlag flag
    ) {
        super.appendHoverText(stack, ctx, tooltip, flag);
        BlockOfProgress.getBlessedJob(stack)
                       .ifPresent(job -> tooltip.add(Compat.translatableStyled(
                               "tooltip.block_of_progress.guaranteed_job",
                               Style.EMPTY.withColor(ChatFormatting.GOLD),
                               Compat.translatable("jobs." + job.rootId())
                       )));
    }
}
