package ca.bradj.questown.commands.test;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * One scripted player action in a helper-chicken scenario. The executor calls the server-side
 * entry point a player's action would reach, then waits {@code postActionWaitTicks}.
 */
public sealed interface ChickenArcScriptedAction {

    int postActionWaitTicks();

    record GiveItem(
            Item item,
            int count,
            int postActionWaitTicks
    ) implements ChickenArcScriptedAction {}

    /** Gives the fake player a town wand already bound to the flag. Needed before {@link WandRightClick}. */
    record GiveBoundWand(
            BlockPos boundFlagOffset,
            int postActionWaitTicks
    ) implements ChickenArcScriptedAction {}

    record WandRightClick(
            BlockPos offset,
            int postActionWaitTicks
    ) implements ChickenArcScriptedAction {}

    record PlaceBlock(
            BlockPos offset,
            BlockState blockState,
            int postActionWaitTicks
    ) implements ChickenArcScriptedAction {}

    /** {@link GiveBoundWand} then {@link WandRightClick} on the door. */
    record RegisterDoorViaWand(
            BlockPos doorOffset,
            int postActionWaitTicks
    ) implements ChickenArcScriptedAction {}

    /** Puts {@code stack} in the first town container. */
    record DepositIntoContainer(
            ItemStack stack,
            int postActionWaitTicks
    ) implements ChickenArcScriptedAction {}

    /** Builds a registered room with walls, a door, a job board and a chest in one step. */
    record SetUpRegisteredRoomWithChest(
            int postActionWaitTicks
    ) implements ChickenArcScriptedAction {}

    record MarkVillagerUiOpened(
            int postActionWaitTicks
    ) implements ChickenArcScriptedAction {}

    record MarkFlagUiOpened(
            int postActionWaitTicks
    ) implements ChickenArcScriptedAction {}

    /** Right-clicks the flag as a player does. */
    record RightClickFlag(
            int postActionWaitTicks
    ) implements ChickenArcScriptedAction {}

    record AdvanceTicks(
            int ticks
    ) implements ChickenArcScriptedAction {
        @Override
        public int postActionWaitTicks() {
            return ticks;
        }
    }

    /** Right-clicks the helper chicken with {@code item} in the fake player's hand. */
    record RightClickChickenWithHand(
            Item item,
            int postActionWaitTicks
    ) implements ChickenArcScriptedAction {}

    /** Runs a command as the fake player, for commands that need a player source. */
    record RunCommand(
            String command,
            int postActionWaitTicks
    ) implements ChickenArcScriptedAction {}

    /** Moves the fake player by setting its position: a teleport or /tp never applies to a fake player. */
    record MovePlayer(
            double x,
            double y,
            double z,
            int postActionWaitTicks
    ) implements ChickenArcScriptedAction {}

    /** Marks the campfire sleep as seen, since a fake player can't sleep. */
    record MarkSleepObserved(
            int postActionWaitTicks
    ) implements ChickenArcScriptedAction {}
}
