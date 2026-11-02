package ca.bradj.questown.commands.test;

import ca.bradj.questown.QT;
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
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Runs one helper-chicken scenario, a phase per tick, through the entry points a player's actions reach. */
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

    /** Advances one tick; true once the scenario has passed or failed. */
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
        if (blueprint.placeFlagViaCommand()) {
            // The command places the flag above the given position, and scenarios aim one below the origin.
            flagPos = origin;
            phase = Phase.RUN_ACTIONS_LOOP;
        } else {
            phase = Phase.PLACE_FLAG;
        }
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
        // These scenarios place their own campfires to test rotation detection.
        if (!blueprint.forceRotationDetected()) {
            phase = Phase.TELEPORT_PLAYER_NEAR_FLAG;
            return;
        }
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
        // Run every no-wait action on the same tick, so the flag never scans half-placed blocks.
        while (actionIndex < blueprint.scriptedActions().size()) {
            ChickenArcScriptedAction action = blueprint.scriptedActions().get(actionIndex);
            actionIndex++;
            dispatchAction(action);
            int wait = Math.max(0, action.postActionWaitTicks());
            if (wait > 0) {
                settleTicks = wait;
                return;
            }
        }
        phase = Phase.WARP;
    }

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
        if (action instanceof ChickenArcScriptedAction.RightClickFlag) {
            handleRightClickFlag();
            return;
        }
        if (action instanceof ChickenArcScriptedAction.AdvanceTicks) {
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
        if (action instanceof ChickenArcScriptedAction.MovePlayer a) {
            handleMovePlayer(a);
            return;
        }
        if (action instanceof ChickenArcScriptedAction.MarkSleepObserved) {
            handleMarkSleepObserved();
            return;
        }
        failAction(action.getClass().getSimpleName(), "no dispatch handler registered");
    }

    private void handleMarkSleepObserved() {
        if (flag == null) {
            failAction("MarkSleepObserved", "no flag BE");
            return;
        }
        flag.setChickenObservedSleepSinceSunset(true);
        CompoundTag tag = Compat.getBlockStoredTagData(flag);
        flag.writeTownData(tag);
        flag.setChanged();
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
        // Not Inventory.add: in 1.19.2 it empties the stack it is given.
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
        if (a.blockState().getBlock() instanceof DoorBlock) {
            // Place the upper half too, so the door is whole.
            level.setBlockAndUpdate(pos,
                    a.blockState().setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
            level.setBlockAndUpdate(pos.above(),
                    a.blockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
            return;
        }
        level.setBlockAndUpdate(pos, a.blockState());
        // setBlock skips the mat's own registration, so register it here.
        if (a.blockState().is(BlocksInit.WELCOME_MAT_BLOCK.get())
                && flag != null) {
            flag.registerWelcomeMat(pos);
        }
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
        // A 9x5 room, big enough that the chest offset is inside it.
        buildRegisteredRoomPerimeter();
        placeRegisteredRoomFurnishings();
        registerRoomFixtures();
    }

    private void buildRegisteredRoomPerimeter() {
        BlockPos doorOffset = HelperChickenBeatOffsets.DOOR_OFFSET;
        // Walls on the perimeter, leaving the door's column open.
        for (int x = 2; x <= 10; x++) {
            for (int z = 2; z <= 6; z++) {
                boolean onPerimeter = (x == 2 || x == 10 || z == 2 || z == 6);
                if (!onPerimeter) continue;
                if (x == doorOffset.getX() && z == doorOffset.getZ()) {
                    continue;
                }
                for (int y = 0; y <= 1; y++) {
                    BlockPos local = new BlockPos(x, y, z);
                    BlockPos world = flagPos.offset(local.rotate(blueprint.startRotation()));
                    level.setBlockAndUpdate(world, Blocks.COBBLESTONE.defaultBlockState());
                }
            }
        }
    }

    private void placeRegisteredRoomFurnishings() {
        BlockPos doorOffset = HelperChickenBeatOffsets.DOOR_OFFSET;
        BlockPos chestOffset = HelperChickenBeatOffsets.CHEST_OFFSET;
        BlockPos signOffset = HelperChickenBeatOffsets.SIGN_OFFSET;
        BlockPos gateCenter = HelperChickenBeatOffsets.GATE_CENTER_OFFSET;
        handlePlaceBlock(new ChickenArcScriptedAction.PlaceBlock(
                doorOffset, Blocks.OAK_DOOR.defaultBlockState(), 0));
        BlockPos chestWorld = flagPos.offset(chestOffset.rotate(blueprint.startRotation()));
        level.setBlockAndUpdate(chestWorld, Blocks.CHEST.defaultBlockState());
        // Placed directly: setBlock skips the sign-to-board conversion and the mat's registration.
        handlePlaceBlock(new ChickenArcScriptedAction.PlaceBlock(
                signOffset, BlocksInit.JOB_BOARD_BLOCK.get().defaultBlockState(), 0));
        handlePlaceBlock(new ChickenArcScriptedAction.PlaceBlock(
                gateCenter, BlocksInit.WELCOME_MAT_BLOCK.get().defaultBlockState(), 0));
    }

    private void registerRoomFixtures() {
        BlockPos doorOffset = HelperChickenBeatOffsets.DOOR_OFFSET;
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

    private void handleRightClickFlag() {
        if (fakePlayer == null || flagPos == null) {
            failAction("RightClickFlag", "fake player or flag missing");
            return;
        }
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(flagPos), Direction.UP, flagPos, false);
        level.getBlockState(flagPos).useWithoutItem(level, fakePlayer, hit);
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
        AABB area = new AABB(flagPos.offset(-hw, -5, -hw)).minmax(new AABB(flagPos.offset(hw, 10, hw)));
        // Only this flag's chicken; earlier scenarios' chickens can wander in.
        HelperChickenEntity chicken = null;
        for (HelperChickenEntity e : level.getEntitiesOfClass(HelperChickenEntity.class, area)) {
            if (e.isAlive() && flagPos.equals(e.getOwnerFlagPos())) {
                chicken = e;
                break;
            }
        }
        if (chicken == null) {
            failAction("RightClickChickenWithHand",
                    "no chicken bound to this flag in arena (check spawn + ownerFlagPos)");
            return;
        }
        fakePlayer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(a.item()));
        chicken.interactAt(fakePlayer, Vec3.ZERO, InteractionHand.MAIN_HAND);
    }

    private void handleMovePlayer(ChickenArcScriptedAction.MovePlayer a) {
        if (fakePlayer == null) {
            failAction("MovePlayer", "fake player is null");
            return;
        }
        // setPos, not teleportTo: a fake player's teleport never applies.
        fakePlayer.setPos(a.x(), a.y(), a.z());
        QT.FLAG_LOGGER.info(
                "[autotest] moved fake player to {} {} {} (now at {})",
                (int) a.x(), (int) a.y(), (int) a.z(), fakePlayer.blockPosition()
        );
    }

    private void handleRunCommand(ChickenArcScriptedAction.RunCommand a) {
        if (fakePlayer == null || server == null) {
            failAction("RunCommand", "fake player or server is null");
            return;
        }
        // Admin permission, with the fake player still the source.
        server.getCommands().performPrefixedCommand(
                fakePlayer.createCommandSourceStack().withPermission(4),
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
        AABB area = new AABB(origin.offset(-20, -5, -20)).minmax(new AABB(origin.offset(20, 10, 20)));
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
