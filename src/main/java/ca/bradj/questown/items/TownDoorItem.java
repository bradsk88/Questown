package ca.bradj.questown.items;

import ca.bradj.questown.Questown;
import ca.bradj.questown.blocks.TownFlagBlock;
import ca.bradj.questown.town.TownFlagBlockEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class TownDoorItem extends Item {
    public static final String ITEM_ID = "town_door";

    public TownDoorItem() {
        super(Questown.DEFAULT_ITEM_PROPS);
    }

    @Override
    public InteractionResult useOn(net.minecraft.world.item.context.UseOnContext ctx) {
        @Nullable ItemStack input = TownFlagBlock.GetFlagInputFromItemNBT(
                ctx.getItemInHand());
        if (input == null) {
            Questown.LOGGER.error("{} is missing flag input", ctx.getItemInHand().getItem().getRegistryName());
            return InteractionResult.FAIL;
        }
        InteractionResult interactionResult = input.getItem().useOn(new UseOnContext(ctx, input));
        if (interactionResult.equals(InteractionResult.CONSUME) && ctx.getLevel() instanceof ServerLevel sl) {
            TownFlagBlockEntity parent = TownFlagBlock.GetParentFromNBT(sl, ctx.getItemInHand());
            parent.registerDoor(ctx.getClickedPos().above());
        }
        return interactionResult;
    }

    private static class UseOnContext extends net.minecraft.world.item.context.UseOnContext {

        public UseOnContext(
                net.minecraft.world.item.context.UseOnContext reference,
                ItemStack swappedItem
        ) {
            super(
                    reference.getLevel(), reference.getPlayer(), reference.getHand(), swappedItem,
                    new BlockHitResult(
                            reference.getClickLocation(), reference.getClickedFace(),
                            reference.getClickedPos(), reference.isInside()
                    )
            );
        }
    }
}
