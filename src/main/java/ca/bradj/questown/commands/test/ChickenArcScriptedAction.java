package ca.bradj.questown.commands.test;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * One scripted player action in a chicken-arc scenario.
 *
 * <p>Each subtype corresponds to a single server-side call the real player would have
 * triggered — giving items to the fake player, right-clicking the wand, placing a
 * block, depositing into a container, running a command, etc. The
 * {@code ChickenArcTestExecutor} dispatches on the concrete subtype and calls the
 * matching server-side entry point directly. No input packets are synthesized.
 *
 * <p>Each action carries its own {@code postActionWaitTicks}; the executor settles
 * that many ticks after dispatching the action before moving on to the next.
 */
public sealed interface ChickenArcScriptedAction {

    /** Ticks of settle to advance after this action fires, before the next action runs. */
    int postActionWaitTicks();

    /** Add {@code count} of {@code item} to the fake player's inventory. */
    record GiveItem(
            Item item,
            int count,
            int postActionWaitTicks
    ) implements ChickenArcScriptedAction {}

    /**
     * Give the fake player a {@code TOWN_WAND} stack whose NBT is already bound to
     * the flag at {@code boundFlagOffset} (structure-local, rotated at dispatch).
     * Required before any {@link WandRightClick} — {@code TownWand.onRightClicked}
     * dereferences the bound-flag NBT without a null guard.
     */
    record GiveBoundWand(
            BlockPos boundFlagOffset,
            int postActionWaitTicks
    ) implements ChickenArcScriptedAction {}

    /**
     * Call {@code TownWand.onRightClicked} against the block at {@code offset}
     * (structure-local, rotated at dispatch) using the fake player's main-hand stack.
     */
    record WandRightClick(
            BlockPos offset,
            int postActionWaitTicks
    ) implements ChickenArcScriptedAction {}

    /** Place {@code blockState} at {@code offset} (structure-local, rotated at dispatch). */
    record PlaceBlock(
            BlockPos offset,
            BlockState blockState,
            int postActionWaitTicks
    ) implements ChickenArcScriptedAction {}

    /**
     * Give a bound wand, then right-click it on the door block at {@code doorOffset}.
     * Composed action — a sugar over {@code GiveBoundWand + WandRightClick}.
     */
    record RegisterDoorViaWand(
            BlockPos doorOffset,
            int postActionWaitTicks
    ) implements ChickenArcScriptedAction {}

    /**
     * Deposit {@code stack} into the first container reachable from the flag
     * (via {@code TownContainers.getAllContainers}). Used for the Worldly-Seeds
     * delivery in F4.
     */
    record DepositIntoContainer(
            ItemStack stack,
            int postActionWaitTicks
    ) implements ChickenArcScriptedAction {}

    /**
     * Bulk-apply the F3 preamble: place walls, a door, a sign (auto-converts to
     * job board), a chest, and wand-register the door. Keeps F4 scenarios from
     * replaying the 14+ individual F3 steps inline. F3's own scenario still
     * runs the fine-grained steps because that's what it's verifying.
     */
    record SetUpRegisteredRoomWithChest(
            int postActionWaitTicks
    ) implements ChickenArcScriptedAction {}

    /** Direct call to {@code ChickenArcUiObservations.markVillagerUiOpened}. */
    record MarkVillagerUiOpened(
            int postActionWaitTicks
    ) implements ChickenArcScriptedAction {}

    /** Direct call to {@code ChickenArcUiObservations.markFlagUiOpened}. */
    record MarkFlagUiOpened(
            int postActionWaitTicks
    ) implements ChickenArcScriptedAction {}

    /** Advance {@code ticks} server ticks with no action. */
    record AdvanceTicks(
            int ticks
    ) implements ChickenArcScriptedAction {
        @Override
        public int postActionWaitTicks() {
            return ticks;
        }
    }

    /**
     * Find the helper chicken and call {@code chicken.interactAt(fakePlayer, ...)}
     * with {@code item} in the fake player's main hand. Drives the F4 seeds hand-off.
     */
    record RightClickChickenWithHand(
            Item item,
            int postActionWaitTicks
    ) implements ChickenArcScriptedAction {}

    /**
     * Dispatch a server command via the fake player's command source so
     * {@code source.getPlayer()} is non-null — some command handlers (e.g.
     * {@code FlagCommand.setBlock}) call {@code APPROACH_TOWN_TRIGGER.trigger(player, ...)}
     * and would NPE with {@code server.createCommandSourceStack()}.
     */
    record RunCommand(
            String command,
            int postActionWaitTicks
    ) implements ChickenArcScriptedAction {}
}
