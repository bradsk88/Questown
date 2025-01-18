package ca.bradj.questown.items;

import ca.bradj.questown.Questown;
import ca.bradj.questown.blocks.FalseDoorBlock;
import ca.bradj.questown.blocks.TownFlagBlock;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.Util;
import ca.bradj.questown.town.TownFlagBlockEntity;
import ca.bradj.questown.town.interfaces.TownInterface;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

public class TownWand extends Item {
    public static final String ITEM_ID = "town_wand";

    public TownWand() {
        super(Questown.DEFAULT_ITEM_PROPS);
    }

    private static ImmutableList<ClickHandler> handlers = ImmutableList.of(
            new DoorHandler(),
            new GateHandler()
    );

    private interface ClickHandler {
        BlockPos getEffectivePosition(
                ServerLevel level,
                BlockPos clickedPos
        );

        boolean handle(
                ServerLevel level,
                BlockPos clickedPos,
                TownFlagBlockEntity parent
        );
    }

    public void onRightClicked(
            Supplier<ServerPlayer> player,
            ServerLevel level,
            BlockPos clickedPos,
            ItemStack itemInHand
    ) {
        TownFlagBlockEntity parent = TownFlagBlock.GetParentFromNBT(level, itemInHand);
        for (ClickHandler handler : handlers) {
            if (handler.handle(level, clickedPos, parent)) {
                return;
            }
        }
        BlockPos bp = parent.getTownFlagBasePos();
        Util.onScreenText(player, "message.wand.clicked_away", bp.getX(), bp.getY(), bp.getZ());
    }

    @Override
    public InteractionResult useOn(UseOnContext p_41427_) {
        InteractionResult x = super.useOn(p_41427_);
        if (p_41427_.getLevel().isClientSide) {
            return InteractionResult.CONSUME;
        }
        ServerLevel level = (ServerLevel) p_41427_.getLevel();
        for (ClickHandler handler : handlers) {
            if (handler.getEffectivePosition(level, p_41427_.getClickedPos()) != null) {
                return InteractionResult.CONSUME;
            }
        }
        if (!x.consumesAction()) {
            ItemStack item = p_41427_.getItemInHand();
            TownInterface parent = TownFlagBlock.GetParentFromNBT(level, item);
            BlockPos bp = parent.getTownFlagBasePos();
            Util.onScreenText(
                    () -> (ServerPlayer) p_41427_.getPlayer(),
                    "message.wand.clicked_away",
                    bp.getX(), bp.getY(), bp.getZ()
            );
        }
        return x;
    }

    @Override
    public void appendHoverText(
            ItemStack item,
            @Nullable Level level,
            List<Component> p_41423_,
            TooltipFlag p_41424_
    ) {
        super.appendHoverText(item, level, p_41423_, p_41424_);
        BlockPos parent = TownFlagBlock.GetParentPosFromNBT(item);
        Style color = Style.EMPTY.withColor(TextColor.parseColor("GRAY"));

        if (parent == null) {
            String key = "tooltips.items.wand.inactive";
            p_41423_.add(Compat.translatableStyled(key, color));
        } else {
            String key = "tooltips.items.wand.active";
            p_41423_.add(Compat.translatableStyled(key, color, parent.getX(), parent.getY(), parent.getZ()));
        }
    }

    private static class DoorHandler implements ClickHandler {
        @Override
        public BlockPos getEffectivePosition(
                ServerLevel level,
                BlockPos clickedPos
        ) {
            BlockState bs = level.getBlockState(clickedPos);
            if (bs.getBlock() instanceof FalseDoorBlock) {
                return clickedPos;
            }

            if (bs.getBlock() instanceof DoorBlock) {
                if (DoubleBlockHalf.UPPER.equals(bs.getValue(DoorBlock.HALF))) {
                    clickedPos = clickedPos.below();
                }
            } else {
                bs = level.getBlockState(clickedPos.above());
                if (bs.getBlock() instanceof DoorBlock) {
                    clickedPos = clickedPos.above();
                } else {
                    return null;
                }
            }
            return clickedPos;
        }

        @Override
        public boolean handle(
                ServerLevel level,
                BlockPos clickedPos,
                TownFlagBlockEntity parent
        ) {
            BlockPos doorPos = getEffectivePosition(level, clickedPos);
            if (doorPos != null) {
                parent.getRoomHandle().registerDoor(doorPos);
                return true;
            }
            return false;
        }
    }

    private static class GateHandler implements ClickHandler {
        @Override
        public BlockPos getEffectivePosition(
                ServerLevel level,
                BlockPos clickedPos
        ) {
            BlockState bs = level.getBlockState(clickedPos);
            if (bs.getBlock() instanceof FenceGateBlock) {
                return clickedPos;
            }
            return null;
        }

        @Override
        public boolean handle(
                ServerLevel level,
                BlockPos clickedPos,
                TownFlagBlockEntity parent
        ) {
            BlockPos doorPos = getEffectivePosition(level, clickedPos);
            if (doorPos != null) {
                parent.getRoomHandle().registerFenceGate(doorPos);
                return true;
            }
            return false;
        }
    }
}
