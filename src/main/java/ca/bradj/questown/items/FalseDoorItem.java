package ca.bradj.questown.items;

import ca.bradj.questown.QT;
import ca.bradj.questown.Questown;
import ca.bradj.questown.blocks.FalseDoorBlock;
import ca.bradj.questown.blocks.TownFlagBlock;
import ca.bradj.questown.core.init.BlocksInit;
import ca.bradj.questown.core.init.items.ItemsInit;
import ca.bradj.questown.town.TownFlagBlockEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

public class FalseDoorItem extends Item {
    public static final String ITEM_ID = "false_door_block";

    public FalseDoorItem() {
        super(Questown.DEFAULT_ITEM_PROPS);
    }

    @Override
    public InteractionResult useOn(net.minecraft.world.item.context.UseOnContext ctx) {
        return placeAndRegisterContainedItem(ctx, super::useOn);
    }

    @NotNull
    private static InteractionResult placeAndRegisterContainedItem(
            net.minecraft.world.item.context.UseOnContext ctx,
            Function<net.minecraft.world.item.context.UseOnContext, InteractionResult> supr
    ) {
        @Nullable ItemStack input = TownFlagBlock.GetFlagInputFromItemNBT(
                ctx.getItemInHand());
        if (input == null) {
            QT.ITEM_LOGGER.error("{} is missing flag input", ForgeRegistries.ITEMS.getKey(ctx.getItemInHand().getItem()));
            return InteractionResult.FAIL;
        }
        if (ctx.getLevel() instanceof ServerLevel sl) {
            sl.setBlockAndUpdate(ctx.getClickedPos().above(), BlocksInit.FALSE_DOOR_BLOCK.get().defaultBlockState());
            TownFlagBlockEntity parent = TownFlagBlock.GetParentFromNBT(sl, ctx.getItemInHand());
            parent.registerDoor(ctx.getClickedPos().above());
            return InteractionResult.CONSUME;
        }
        return InteractionResult.FAIL;
    }

}
