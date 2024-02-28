package ca.bradj.questown.items;

import ca.bradj.questown.QT;
import ca.bradj.questown.Questown;
import ca.bradj.questown.blocks.TownFlagBlock;
import ca.bradj.questown.town.TownFlagBlockEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

public class TownFenceGateItem extends Item {
    public static final String ITEM_ID = "town_fence_gate";

    public TownFenceGateItem() {
        super(Questown.DEFAULT_ITEM_PROPS);
    }

    @Override
    public InteractionResult useOn(net.minecraft.world.item.context.UseOnContext ctx) {
        @Nullable ItemStack input = TownFlagBlock.GetFlagInputFromItemNBT(
                ctx.getItemInHand());
        if (input == null) {
            QT.BLOCK_LOGGER.error("{} is missing flag input", ForgeRegistries.ITEMS.getKey(ctx.getItemInHand().getItem()));
            return InteractionResult.FAIL;
        }
        InteractionResult interactionResult = input.getItem().useOn(new UseOnContext(ctx, input));
        if (interactionResult.equals(InteractionResult.CONSUME) && ctx.getLevel() instanceof ServerLevel sl) {
            TownFlagBlockEntity parent = TownFlagBlock.GetParentFromNBT(sl, ctx.getItemInHand());
            parent.getRoomHandle().registerFenceGate(ctx.getClickedPos().above());
        }
        return interactionResult;
    }
}
