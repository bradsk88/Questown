package ca.bradj.questown.items;

import ca.bradj.questown.Questown;
import ca.bradj.questown.blocks.FalseDoorBlock;
import ca.bradj.questown.blocks.RoomBlock;
import ca.bradj.questown.blocks.TownFlagBlock;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.Util;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import ca.bradj.questown.logic.TownCycle;
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
            new GateHandler(),
            new RoomBlockHandler()
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

        BlockState clickedState = level.getBlockState(clickedPos);
        if (clickedState.is(Blocks.CAMPFIRE)) {
            handleCampfireClick(player, level, clickedPos, clickedState, parent);
            return;
        }

        for (ClickHandler handler : handlers) {
            if (handler.handle(level, clickedPos, parent)) {
                return;
            }
        }
        BlockPos bp = parent.getTownFlagBasePos();
        Util.onScreenText(player, "message.wand.clicked_away", bp.getX(), bp.getY(), bp.getZ());
    }

    /**
     * Wand-on-campfire has two branches sharing one flag-radius gate:
     * <ul>
     *   <li>unlit + registered → light it (R14 addition; no sleep).</li>
     *   <li>lit + registered   → fall through to the existing sleep path.</li>
     * </ul>
     * Outside a town's registered campfire the wand is inert on campfires —
     * this is a behavior change from prior builds, which previously let any
     * campfire anywhere trigger the sleep attempt and rejected it inside the
     * handler. Now both paths gate up front and share the same rejection.
     */
    private void handleCampfireClick(
            Supplier<ServerPlayer> player,
            ServerLevel level,
            BlockPos clickedPos,
            BlockState clickedState,
            TownFlagBlockEntity parent
    ) {
        boolean registered = TownCycle.findCampfire(parent.getBlockPos(), level)
                                      .filter(clickedPos::equals)
                                      .isPresent();
        if (!registered) {
            Util.onScreenText(player, "message.wand.campfire.not_registered");
            return;
        }
        boolean lit = clickedState.hasProperty(CampfireBlock.LIT) && clickedState.getValue(CampfireBlock.LIT);
        if (!lit) {
            level.setBlockAndUpdate(clickedPos, clickedState.setValue(CampfireBlock.LIT, true));
            level.playSound(
                    null, clickedPos,
                    SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS,
                    1.0F, level.getRandom().nextFloat() * 0.4F + 0.8F
            );
            return;
        }
        CampfireSleepHandler.beginCampfireSleep(player.get(), level, clickedPos, parent);
    }

    @Override
    public InteractionResult useOn(UseOnContext p_41427_) {
        InteractionResult x = super.useOn(p_41427_);
        if (p_41427_.getLevel().isClientSide) {
            return InteractionResult.CONSUME;
        }
        ServerLevel level = (ServerLevel) p_41427_.getLevel();
        if (level.getBlockState(p_41427_.getClickedPos()).is(Blocks.CAMPFIRE)) {
            return InteractionResult.CONSUME;
        }
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

    private static class RoomBlockHandler implements ClickHandler {
        @Override
        public BlockPos getEffectivePosition(
                ServerLevel level,
                BlockPos clickedPos
        ) {
            BlockState bs = level.getBlockState(clickedPos);
            if (bs.getBlock() instanceof RoomBlock) {
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
            BlockState bs = level.getBlockState(clickedPos);
            if ((bs.getBlock() instanceof RoomBlock rb)) {
                parent.getRoomHandle().registerBlockAsRoom(RoomBlock.getRoomId(rb), clickedPos);
                return true;
            }
            return false;
        }
    }
}
