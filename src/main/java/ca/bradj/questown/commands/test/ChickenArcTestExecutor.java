package ca.bradj.questown.commands.test;

import ca.bradj.questown.blocks.TownFlagBlock;
import ca.bradj.questown.core.init.BlocksInit;
import ca.bradj.questown.core.init.items.ItemsInit;
import ca.bradj.questown.integration.minecraft.MCTownState;
import ca.bradj.questown.items.TownWand;
import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mobs.helperchicken.ChickenArcUiObservations;
import ca.bradj.questown.mobs.helperchicken.ChickenScaffoldingLayout;
import ca.bradj.questown.mobs.helperchicken.HelperChickenBeatOffsets;
import ca.bradj.questown.mobs.helperchicken.HelperChickenEntity;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.HelperChickenSpawnController;
import ca.bradj.questown.town.TownContainers;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Phase machine that drives one {@link ChickenArcBlueprint} end-to-end inside
 * {@code AutoTestRunner}'s tick loop. See the plan's U3 Technical Design block
 * for the phase graph.
 *
 * <p>Per-action dispatch uses an {@code instanceof}-pattern chain over the
 * sealed {@link ChickenArcScriptedAction} hierarchy (switch-on-sealed is a
 * Java 21 feature; this project targets Java 17). The U2 exhaustiveness test
 * {@code ChickenArcBlueprintTest.scriptedAction_subtypeCountMatchesSealedHierarchy}
 * guards against forgotten subtypes. All handlers call the same server-side
 * entry points the real player would trigger.
 */
public final class ChickenArcTestExecutor {

    private enum Phase {
        RESET_ARENA,
        PLACE_FLAG,
        WAIT_FOR_INIT,
        FORCE_ROTATION_STATE,
        PLACE_SCAFFOLDING,
        TELEPORT_PLAYER_NEAR_FLAG,
        SPAWN_CHICKEN,
        RUN_ACTIONS_LOOP,
        WARP,
        SETTLE_AFTER_WARP,
        CHECK_RESULTS,
        CLEANUP,
        DONE
    }

    private final ServerLevel level;
    private final MinecraftServer server;
    private final ServerPlayer fakePlayer;
    private final BlockPos origin;
    private final ChickenArcBlueprint blueprint;
    private final TestOutput output;

    private Phase phase = Phase.RESET_ARENA;
    private BlockPos flagPos;
    private TownFlagBlockEntity flag;
    private int actionIndex = 0;
    private int waitTicks = 0;
    private int maxWaitTicks = 0;
    private int settleTicks = 0;
    private boolean passed = false;
    private int passedAssertions = 0;
    private int totalAssertions = 0;

    public ChickenArcTestExecutor(
            ServerLevel level,
            MinecraftServer server,
            ServerPlayer fakePlayer,
            BlockPos origin,
            ChickenArcBlueprint blueprint,
            TestOutput output
    ) {
        this.level = level;
        this.server = server;
        this.fakePlayer = fakePlayer;
        this.origin = origin;
        this.blueprint = blueprint;
        this.output = output;
    }

    /**
     * Advance one server tick. Returns {@code true} once the scenario is done
     * (pass or fail). The outer driver (U4's {@code ChickenArcAllExecutor})
     * polls this each tick.
     */
    public boolean tick() {
        try {
            return runOneTick();
        } catch (Exception e) {
            failPhase(phase.name(), e.toString());
            phase = Phase.CLEANUP;
            return false;
        }
    }

    public boolean getPassed() {
        return passed;
    }

    public int getPassedAssertions() {
        return passedAssertions;
    }

    public int getTotalAssertions() {
        return totalAssertions;
    }

    private boolean runOneTick() {
        switch (phase) {
            case RESET_ARENA -> resetArena();
            case PLACE_FLAG -> placeFlag();
            case WAIT_FOR_INIT -> waitForInit();
            case FORCE_ROTATION_STATE -> forceRotationState();
            case PLACE_SCAFFOLDING -> placeScaffolding();
            case TELEPORT_PLAYER_NEAR_FLAG -> teleportPlayerNearFlag();
            case SPAWN_CHICKEN -> spawnChicken();
            case RUN_ACTIONS_LOOP -> runActionsLoop();
            case WARP -> warp();
            case SETTLE_AFTER_WARP -> settleAfterWarp();
            case CHECK_RESULTS -> checkResults();
            case CLEANUP -> cleanup();
            case DONE -> { return true; }
        }
        return phase == Phase.DONE;
    }

    private void resetArena() {
        int halfWidth = blueprint.effectiveHalfWidth();
        TestArenaPreparer.PreparerOptions opts = new TestArenaPreparer.PreparerOptions(
                halfWidth, true, true, true
        );
        TestArenaPreparer.destroyNearbyFlags(level, origin, opts, fakePlayer);
        TestArenaPreparer.flatten(level, origin, opts);
        phase = blueprint.placeFlagViaCommand() ? Phase.RUN_ACTIONS_LOOP : Phase.PLACE_FLAG;
    }

    private void placeFlag() {
        flagPos = origin;
        level.setBlockAndUpdate(flagPos, BlocksInit.COBBLESTONE_TOWN_FLAG.get().defaultBlockState());
        msg("Placed town flag at " + flagPos.toShortString());
        phase = Phase.WAIT_FOR_INIT;
        waitTicks = 0;
        maxWaitTicks = 200;
    }

    private void waitForInit() {
        BlockEntity be = level.getBlockEntity(flagPos);
        if (!(be instanceof TownFlagBlockEntity fbe)) {
            if (waitTicks++ >= maxWaitTicks) {
                failPhase("PLACE_FLAG", "town flag block entity not found within " + maxWaitTicks + " ticks");
                phase = Phase.CLEANUP;
            }
            return;
        }
        flag = fbe;
        if (!flag.isInitialized()) {
            if (waitTicks++ >= maxWaitTicks) {
                failPhase("PLACE_FLAG", "flag failed to initialize within " + maxWaitTicks + " ticks");
                phase = Phase.CLEANUP;
            }
            return;
        }
        msg("Town flag initialized");
        phase = blueprint.forceRotationDetected()
                ? Phase.FORCE_ROTATION_STATE
                : Phase.PLACE_SCAFFOLDING;
    }

    private void forceRotationState() {
        flag.setChickenStructureRotation(blueprint.startRotation());
        flag.setChickenRotationDetected(true);
        flag.setChickenArcForfeit(false);
        flag.setChickenEverSpawned(false);
        CompoundTag tag = Compat.getBlockStoredTagData(flag);
        flag.writeTownData(tag);
        flag.setChanged();
        phase = Phase.PLACE_SCAFFOLDING;
    }

    private void placeScaffolding() {
        List<ChickenScaffoldingLayout.BlockPlacement> layout =
                ChickenScaffoldingLayout.forRotation(blueprint.startRotation());
        for (ChickenScaffoldingLayout.BlockPlacement p : layout) {
            level.setBlockAndUpdate(flagPos.offset(p.offset()), p.blockState());
        }
        phase = Phase.TELEPORT_PLAYER_NEAR_FLAG;
    }

    private void teleportPlayerNearFlag() {
        if (fakePlayer != null) {
            fakePlayer.teleportTo(
                    level,
                    flagPos.getX() + 0.5D,
                    flagPos.getY() + 1.0D,
                    flagPos.getZ() + 0.5D,
                    0F, 0F
            );
        }
        phase = Phase.SPAWN_CHICKEN;
    }

    private void spawnChicken() {
        if (flag != null) {
            HelperChickenSpawnController.tick(flag);
        }
        phase = Phase.RUN_ACTIONS_LOOP;
        actionIndex = 0;
        settleTicks = 0;
    }

    private void runActionsLoop() {
        if (settleTicks > 0) {
            settleTicks--;
            return;
        }
        if (actionIndex >= blueprint.scriptedActions().size()) {
            phase = Phase.WARP;
            return;
        }
        ChickenArcScriptedAction action = blueprint.scriptedActions().get(actionIndex);
        actionIndex++;
        dispatchAction(action);
        settleTicks = Math.max(0, action.postActionWaitTicks());
    }

    /**
     * Dispatch to the per-subtype handler. Uses {@code instanceof} pattern-match
     * chains rather than switch-on-sealed (Java 17 target predates the latter).
     * The compiler still flags missing subtypes at
     * {@link ChickenArcBlueprintTest#scriptedAction_subtypeCountMatchesSealedHierarchy}
     * runtime — the U2 exhaustiveness test counts {@code 12} permitted subclasses
     * and fails when that number drifts, which forces a matching update here.
     */
    private void dispatchAction(ChickenArcScriptedAction action) {
        if (action instanceof ChickenArcScriptedAction.GiveItem a) {
            handleGiveItem(a);
            return;
        }
        if (action instanceof ChickenArcScriptedAction.GiveBoundWand a) {
            handleGiveBoundWand(a);
            return;
        }
        if (action instanceof ChickenArcScriptedAction.WandRightClick a) {
            handleWandRightClick(a);
            return;
        }
        if (action instanceof ChickenArcScriptedAction.PlaceBlock a) {
            handlePlaceBlock(a);
            return;
        }
        if (action instanceof ChickenArcScriptedAction.RegisterDoorViaWand a) {
            handleRegisterDoorViaWand(a);
            return;
        }
        if (action instanceof ChickenArcScriptedAction.DepositIntoContainer a) {
            handleDepositIntoContainer(a);
            return;
        }
        if (action instanceof ChickenArcScriptedAction.SetUpRegisteredRoomWithChest a) {
            handleSetUpRegisteredRoomWithChest(a);
            return;
        }
        if (action instanceof ChickenArcScriptedAction.MarkVillagerUiOpened) {
            handleMarkVillagerUiOpened();
            return;
        }
        if (action instanceof ChickenArcScriptedAction.MarkFlagUiOpened) {
            handleMarkFlagUiOpened();
            return;
        }
        if (action instanceof ChickenArcScriptedAction.AdvanceTicks) {
            // settle-only; the phase machine consumes postActionWaitTicks()
            return;
        }
        if (action instanceof ChickenArcScriptedAction.RightClickChickenWithHand a) {
            handleRightClickChicken(a);
            return;
        }
        if (action instanceof ChickenArcScriptedAction.RunCommand a) {
            handleRunCommand(a);
            return;
        }
        failAction(action.getClass().getSimpleName(), "no dispatch handler registered");
    }

    private void handleGiveItem(ChickenArcScriptedAction.GiveItem a) {
        if (fakePlayer == null) {
            failAction("GiveItem", "fake player is null");
            return;
        }
        ItemStack stack = new ItemStack(a.item(), a.count());
        fakePlayer.getInventory().add(stack);
    }

    private void handleGiveBoundWand(ChickenArcScriptedAction.GiveBoundWand a) {
        if (fakePlayer == null) {
            failAction("GiveBoundWand", "fake player is null");
            return;
        }
        if (flag == null) {
            failAction("GiveBoundWand", "no flag BE to bind wand to");
            return;
        }
        ItemStack wand = new ItemStack(ItemsInit.TOWN_WAND.get());
        BlockPos targetFlagPos = flagPos.offset(a.boundFlagOffset().rotate(blueprint.startRotation()));
        TownFlagBlock.StoreParentOnNBT(wand, targetFlagPos);
        fakePlayer.getInventory().add(wand);
        fakePlayer.setItemInHand(InteractionHand.MAIN_HAND, wand);
    }

    private void handleWandRightClick(ChickenArcScriptedAction.WandRightClick a) {
        if (fakePlayer == null) {
            failAction("WandRightClick", "fake player is null");
            return;
        }
        ItemStack inHand = fakePlayer.getMainHandItem();
        if (inHand.isEmpty()) {
            failAction("WandRightClick", "fake player has no main-hand item (did you forget GiveBoundWand?)");
            return;
        }
        if (!(inHand.getItem() instanceof TownWand wand)) {
            failAction("WandRightClick", "main-hand item is not a TownWand: " + inHand.getItem());
            return;
        }
        BlockPos clickPos = flagPos.offset(a.offset().rotate(blueprint.startRotation()));
        wand.onRightClicked(() -> fakePlayer, level, clickPos, inHand);
    }

    private void handlePlaceBlock(ChickenArcScriptedAction.PlaceBlock a) {
        BlockPos pos = flagPos.offset(a.offset().rotate(blueprint.startRotation()));
        level.setBlockAndUpdate(pos, a.blockState());
    }

    private void handleRegisterDoorViaWand(ChickenArcScriptedAction.RegisterDoorViaWand a) {
        handleGiveBoundWand(new ChickenArcScriptedAction.GiveBoundWand(BlockPos.ZERO, 0));
        handleWandRightClick(new ChickenArcScriptedAction.WandRightClick(a.doorOffset(), 0));
    }

    private void handleDepositIntoContainer(ChickenArcScriptedAction.DepositIntoContainer a) {
        if (flag == null) {
            failAction("DepositIntoContainer", "no flag BE");
            return;
        }
        List<ContainerTarget<ca.bradj.questown.integration.minecraft.MCContainer,
                ca.bradj.questown.integration.minecraft.MCTownItem>> containers =
                TownContainers.getAllContainers(flag, level);
        if (containers.isEmpty()) {
            failAction("DepositIntoContainer", "no containers registered on flag");
            return;
        }
        ContainerTarget<?, ca.bradj.questown.integration.minecraft.MCTownItem> target = containers.get(0);
        BlockPos containerPos = target.getBlockPos();
        if (!(level.getBlockEntity(containerPos)
                instanceof net.minecraft.world.Container container)) {
            failAction("DepositIntoContainer", "container BE at " + containerPos.toShortString() + " is not a Container");
            return;
        }
        ItemStack remaining = a.stack().copy();
        for (int i = 0; i < container.getContainerSize() && !remaining.isEmpty(); i++) {
            ItemStack slot = container.getItem(i);
            if (slot.isEmpty()) {
                container.setItem(i, remaining.copy());
                remaining.setCount(0);
                break;
            }
        }
        container.setChanged();
    }

    private void handleSetUpRegisteredRoomWithChest(ChickenArcScriptedAction.SetUpRegisteredRoomWithChest a) {
        // Place the 5x5 cobblestone perimeter (matches ChickenScaffoldingLayout's room
        // footprint), put an oak door in the DOOR_OFFSET gap, place a sign (auto-converts
        // to a job board when near a flag), plant a chest at CHEST_OFFSET, then wand-register
        // the door so TownContainers.getAllContainers finds the chest.
        for (int x = 2; x <= 6; x++) {
            for (int z = 2; z <= 6; z++) {
                boolean onPerimeter = (x == 2 || x == 6 || z == 2 || z == 6);
                if (!onPerimeter) continue;
                BlockPos local = new BlockPos(x, 0, z);
                BlockPos world = flagPos.offset(local.rotate(blueprint.startRotation()));
                level.setBlockAndUpdate(world, Blocks.COBBLESTONE.defaultBlockState());
            }
        }
        BlockPos doorOffset = HelperChickenBeatOffsets.DOOR_OFFSET;
        BlockPos chestOffset = HelperChickenBeatOffsets.CHEST_OFFSET;
        BlockPos signOffset = HelperChickenBeatOffsets.SIGN_OFFSET;
        BlockPos doorWorld = flagPos.offset(doorOffset.rotate(blueprint.startRotation()));
        BlockPos chestWorld = flagPos.offset(chestOffset.rotate(blueprint.startRotation()));
        BlockPos signWorld = flagPos.offset(signOffset.rotate(blueprint.startRotation()));
        level.setBlockAndUpdate(doorWorld, Blocks.OAK_DOOR.defaultBlockState());
        level.setBlockAndUpdate(chestWorld, Blocks.CHEST.defaultBlockState());
        level.setBlockAndUpdate(signWorld, Blocks.OAK_SIGN.defaultBlockState());
        // Wand-register the door.
        handleRegisterDoorViaWand(new ChickenArcScriptedAction.RegisterDoorViaWand(doorOffset, 0));
    }

    private void handleMarkVillagerUiOpened() {
        if (flagPos == null) {
            failAction("MarkVillagerUiOpened", "flag not placed yet");
            return;
        }
        ChickenArcUiObservations.markVillagerUiOpened(level, flagPos);
    }

    private void handleMarkFlagUiOpened() {
        if (flagPos == null) {
            failAction("MarkFlagUiOpened", "flag not placed yet");
            return;
        }
        ChickenArcUiObservations.markFlagUiOpened(level, flagPos);
    }

    private void handleRightClickChicken(ChickenArcScriptedAction.RightClickChickenWithHand a) {
        if (fakePlayer == null) {
            failAction("RightClickChickenWithHand", "fake player is null");
            return;
        }
        if (flagPos == null) {
            failAction("RightClickChickenWithHand", "flag not placed yet");
            return;
        }
        int hw = blueprint.effectiveHalfWidth();
        AABB area = new AABB(
                flagPos.offset(-hw, -5, -hw),
                flagPos.offset(hw, 10, hw)
        );
        List<HelperChickenEntity> chickens = level.getEntitiesOfClass(HelperChickenEntity.class, area);
        if (chickens.isEmpty()) {
            failAction("RightClickChickenWithHand", "no chicken in arena");
            return;
        }
        HelperChickenEntity chicken = chickens.get(0);
        fakePlayer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(a.item()));
        chicken.interactAt(fakePlayer, Vec3.ZERO, InteractionHand.MAIN_HAND);
    }

    private void handleRunCommand(ChickenArcScriptedAction.RunCommand a) {
        if (fakePlayer == null || server == null) {
            failAction("RunCommand", "fake player or server is null");
            return;
        }
        server.getCommands().performPrefixedCommand(
                fakePlayer.createCommandSourceStack(),
                a.command()
        );
    }

    private void warp() {
        if (flag != null && blueprint.warpAmount() > 0) {
            MCTownState state = flag.warpTime(blueprint.warpAmount());
            if (state == null) {
                failPhase("WARP", "warpTime returned null state");
            }
        }
        phase = Phase.SETTLE_AFTER_WARP;
        waitTicks = 0;
        maxWaitTicks = Math.max(0, blueprint.postActionSettleTicks());
    }

    private void settleAfterWarp() {
        if (waitTicks++ >= maxWaitTicks) {
            phase = Phase.CHECK_RESULTS;
        }
    }

    private void checkResults() {
        if (flag == null) {
            // Scenario never placed a flag (e.g. command-placed and the command failed).
            BlockEntity be = flagPos != null ? level.getBlockEntity(flagPos) : null;
            if (be instanceof TownFlagBlockEntity fbe) {
                flag = fbe;
            }
        }
        if (flag == null) {
            failPhase("CHECK_RESULTS", "no flag BE available for assertions");
            phase = Phase.CLEANUP;
            return;
        }
        ChickenArcResultChecker.Result r = ChickenArcResultChecker.check(
                blueprint.name(),
                level,
                flag,
                flagPos != null ? flagPos : origin,
                blueprint.effectiveHalfWidth(),
                blueprint.expectation(),
                output,
                null
        );
        passed = r.passed();
        passedAssertions = r.passedCount();
        totalAssertions = r.totalCount();
        phase = Phase.CLEANUP;
    }

    private void cleanup() {
        TestArenaPreparer.PreparerOptions opts = new TestArenaPreparer.PreparerOptions(
                blueprint.effectiveHalfWidth(), true, true, true
        );
        TestArenaPreparer.destroyNearbyFlags(level, origin, opts, fakePlayer);
        TestArenaPreparer.flatten(level, origin, opts);
        // Kill any lingering visitors just in case.
        AABB area = new AABB(origin.offset(-20, -5, -20), origin.offset(20, 10, 20));
        for (VisitorMobEntity e : level.getEntitiesOfClass(VisitorMobEntity.class, area)) {
            e.kill();
        }
        phase = Phase.DONE;
    }

    private void failAction(String actionName, String reason) {
        output.msg(AutotestLogFormatter.format(
                AutotestLogFormatter.TRACK_CHICKEN_ARC,
                blueprint.name(),
                false,
                "action " + actionName,
                reason
        ));
    }

    private void failPhase(String phaseName, String reason) {
        output.msg(AutotestLogFormatter.format(
                AutotestLogFormatter.TRACK_CHICKEN_ARC,
                blueprint.name(),
                false,
                phaseName,
                reason
        ));
    }

    private void msg(String text) {
        output.msg(text);
    }
}
