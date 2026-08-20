package ca.bradj.questown.commands.test;

import ca.bradj.questown.QT;
import ca.bradj.questown.Questown;
import ca.bradj.questown.blocks.FlagPhase;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.blocks.TownFlagBlock;
import ca.bradj.questown.items.CampfireSleepHandler;
import ca.bradj.questown.items.RelocationDeedItem;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import ca.bradj.questown.town.entity.TownRelocation;
import ca.bradj.questown.town.entity.TownRelocation.FarFixturePolicy;
import ca.bradj.questown.town.entity.TownRelocation.RelocationResult;
import ca.bradj.questown.town.entity.TownRoomsHandle;
import ca.bradj.questown.items.TownWand;
import ca.bradj.questown.town.rooms.DoorTrouble;
import ca.bradj.questown.town.rooms.TownPosition;
import net.minecraft.nbt.CompoundTag;
import ca.bradj.questown.commands.test.TestBlueprint.BlockPlacement;
import ca.bradj.questown.commands.test.TestBlueprint.RoomType;
import ca.bradj.questown.commands.test.TestExpectation.ExpectedProduct;
import ca.bradj.questown.commands.test.TestExpectation.ExpectedContainerContent;
import ca.bradj.questown.core.init.BlocksInit;
import ca.bradj.questown.core.init.items.ItemsInit;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.mobs.visitor.TownieNeed;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.integration.minecraft.MCTownState;
import ca.bradj.questown.items.StockRequestItem;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.requests.WorkRequest;
import ca.bradj.questown.jobs.ServerJobsRegistry;
import ca.bradj.questown.jobs.WorksBehaviour;
import ca.bradj.questown.jobs.declarative.AbstractWorldInteraction;
import ca.bradj.questown.town.TownState;
import ca.bradj.questown.town.interfaces.VillagerHolder;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.town.entity.TownVillagerLearningHandle;
import ca.bradj.questown.town.special.SpecialQuests;
import com.google.common.collect.ImmutableSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class TestBlueprintRegistry {

    public interface AnyTestEntry {
        String name();
        String category();
    }

    public record TestEntry(String name, JobID jobId, TestBlueprint blueprint, String category)
            implements AnyTestEntry {}

    /** A self-reporting boolean check that only needs the {@link ServerLevel} (no town build). */
    public record LevelCheck(String name, String category, Function<ServerLevel, Boolean> check)
            implements AnyTestEntry {}

    public static @Nullable TestBlueprint get(JobID jobId) {
        if ("farmer".equals(jobId.rootId())) {
            return farmerBlueprint();
        }
        if ("cook".equals(jobId.rootId())) {
            return cookBlueprint();
        }
        if ("baker".equals(jobId.rootId())) {
            return bakerBlueprint();
        }
        if ("crafter".equals(jobId.rootId())) {
            if (isArmorerJob(jobId)) {
                return armorerBlueprint();
            }
            return crafterBlueprint();
        }
        if ("smelter".equals(jobId.rootId())) {
            return smelterBlueprint();
        }
        if ("soup_cook".equals(jobId.rootId())) {
            return soupCookBlueprint();
        }
        if ("explorer".equals(jobId.rootId())) {
            return explorerBlueprint();
        }
        if ("gatherer".equals(jobId.rootId())) {
            return gathererBlueprint();
        }
        if ("hunter".equals(jobId.rootId())) {
            return hunterBlueprint();
        }
        if ("miner".equals(jobId.rootId())) {
            return minerBlueprint();
        }
        if ("fisher".equals(jobId.rootId())) {
            return fisherBlueprint();
        }
        if ("arborist".equals(jobId.rootId())) {
            return arboristCutTreesBlueprint();
        }
        if ("organizer".equals(jobId.rootId())) {
            return organizerFetchBlueprint();
        }
        return null;
    }

    public static List<AnyTestEntry> getTestableJobs() {
        List<AnyTestEntry> jobs = new ArrayList<>();

        // Core job tests
        jobs.add(entry(new JobID("farmer", "harvest_wheat"), farmerBlueprint().withCustomAssertion(
                (level, flagPos, town, output) -> {
                    // Counterpart to the no_supplies case: a supplied farmer never asks for items.
                    List<TownieNeed> needs = town.getVillagerHandle().entities().stream()
                            .map(v -> ((VisitorMobEntity) v).getNeed())
                            .toList();
                    output.msg("Townie needs at end of supplied farmer run: " + needs);
                    return needs.stream().allMatch(n -> n == TownieNeed.NONE);
                })));
        jobs.add(entry(new JobID("cook", "simple_furnace_food"), cookBlueprint()));
        jobs.add(entry(new JobID("baker", "bread"), bakerBlueprint()));
        jobs.add(entry(new JobID("crafter", "stick"), crafterBlueprint()));
        jobs.add(entry(new JobID("crafter", "wooden_axe"), blacksmithBlueprint()));
        jobs.add(entry(new JobID("smelter", "process_ore"), smelterBlueprint()));
        jobs.add(entry(new JobID("soup_cook", "one_mushroom_stew"), soupCookBlueprint()));
        jobs.add(entry(new JobID("gatherer", "axe"), gathererBlueprint()));
        jobs.add(entry(new JobID("explorer", "explore"), explorerBlueprint()));
        jobs.add(entry(new JobID("hunter", "sword"), hunterBlueprint()));
        jobs.add(entry(new JobID("miner", "coal"), minerBlueprint()));
        jobs.add(entry(new JobID("fisher", "fish"), fisherBlueprint()));
        jobs.add(entry(new JobID("crafter", "leather_boots"), armorerBlueprint()));
        jobs.add(entry(new JobID("arborist", "cut_trees"), arboristCutTreesBlueprint()));
        jobs.add(entry(new JobID("arborist", "plant_sapling"), arboristPlantSaplingBlueprint()));
        jobs.add(edgeCaseEntry("arborist", "cut_trees", "full_cycle", arboristFullCycleBlueprint()));

        // Organizer/fetch (un-archived). Phase A is the realtime green baseline; Phase B exercises
        // the warp fetch-relocation model (RelocateRequestedItemWarpRule) and now expects the same
        // source->target conservation to hold under warp.
        jobs.add(entry(new JobID("organizer", "fetch"), organizerFetchBlueprint()));
        jobs.add(edgeCaseEntry("organizer", "fetch", "warp", organizerFetchWarpBlueprint()));

        // Edge case tests
        jobs.add(edgeCaseEntry("farmer", "harvest_wheat", "night_start", farmerNightStartBlueprint()));
        jobs.add(edgeCaseEntry("farmer", "harvest_wheat", "all_night", farmerAllNightBlueprint()));
        jobs.add(edgeCaseEntry("farmer", "harvest_wheat", "3_day", farmerMultiDayBlueprint()));
        jobs.add(edgeCaseEntry("farmer", "harvest_wheat", "no_supplies", farmerNoSuppliesBlueprint()));
        jobs.add(edgeCaseEntry("gatherer", "axe", "tool_durability", gathererToolDurabilityBlueprint()));
        jobs.add(edgeCaseEntry("farmer", "harvest_wheat", "2_villagers", farmerTwoVillagersBlueprint()));
        jobs.add(edgeCaseEntry("farmer", "harvest_wheat", "warp_then_realtime", farmerRealtimeBlueprint()));
        jobs.add(edgeCaseEntry("farmer", "harvest_wheat", "proficiency_parity", proficiencyParityBlueprint()));
        jobs.add(edgeCaseEntry("gatherer", "axe", "short_absence", gathererShortAbsenceBlueprint()));
        jobs.add(edgeCaseEntry("gatherer", "axe", "sleep_jump", gathererSleepJumpBlueprint()));
        jobs.add(edgeCaseEntry("gatherer", "axe", "absence_then_sleep", gathererAbsenceThenSleepBlueprint()));

        // Eating tests
        jobs.add(eatingEntry("eat_no_table", eatNoTableBlueprint()));
        jobs.add(eatingEntry("dine_at_time", dineAtTimeBlueprint()));
        jobs.add(eatingEntry("eat_raw_food", eatRawFoodBlueprint()));
        jobs.add(eatingEntryDirect("eat_direct", eatDirectBlueprint()));

        // Worldgen tests
        jobs.add(emptyTownStructureCheck());
        jobs.add(emptyTownStructureSetCheck());

        // UI tests
        jobs.add(jobBoardKnowledgeGatingCheck());
        jobs.add(jobBoardRealUnlockGatingCheck());

        // Flag relocation (#199): non-job ritual scenarios
        jobs.add(flagEntry("town_shutdown", townShutdownBlueprint()));
        jobs.add(flagEntry("deed_issue_and_recover", deedIssueAndRecoverBlueprint()));
        jobs.add(flagEntry("relocate_nearby", relocateNearbyBlueprint()));
        jobs.add(flagEntry("relocate_far", relocateFarBlueprint()));
        jobs.add(flagEntry("deed_consumed_on_place", deedConsumedOnPlaceBlueprint()));
        jobs.add(flagEntry("campfire_sleep_preserves_flag", campfireSleepPreservesFlagBlueprint()));
        jobs.add(flagEntry("dead_door_bubbled", deadDoorBubbledBlueprint()));

        // Perf measurements (always pass; they report timings). Baseline first so the pair can be
        // compared within a single run.
        jobs.add(perfEntry("town_small", perfBlueprint(1, 0, 2000)));
        jobs.add(perfEntry("town_large", perfBlueprint(20, 15, 2000)));

        return jobs;
    }

    public static List<AnyTestEntry> getTestsByCategory(String category) {
        return getTestableJobs().stream()
                .filter(e -> category.equals(e.category()))
                .toList();
    }

    /**
     * Resolves the jobs-track scenarios for a category selector. {@code null} or
     * the {@code "jobs"} meta-bucket means "every testable job"; any other value
     * narrows to that category.
     */
    public static List<AnyTestEntry> resolveJobs(@Nullable String category) {
        return (category == null || "jobs".equals(category))
                ? getTestableJobs()
                : getTestsByCategory(category);
    }

    private static TestEntry entry(JobID id, TestBlueprint bp) {
        return new TestEntry(id.rootId() + "/" + id.jobId(), id, bp, "warp");
    }

    private static TestEntry eatingEntry(String name, TestBlueprint bp) {
        JobID id = new JobID("gatherer", "axe");
        return new TestEntry("eating/" + name, id, bp, "eating");
    }

    private static TestEntry eatingEntryDirect(String name, TestBlueprint bp) {
        JobID id = new JobID("gatherer", "dining_no_table");
        return new TestEntry("eating/" + name, id, bp, "eating");
    }

    private static TestEntry edgeCaseEntry(String root, String job, String variant, TestBlueprint bp) {
        JobID id = new JobID(root, job);
        return new TestEntry(root + "/" + job + " [" + variant + "]", id, bp, "warp");
    }

    // A non-job ritual scenario. The benign in-town job keeps the townies near the flag (a leaver
    // would wander off and miss the recall window); the ritual itself is what's under test.
    private static TestEntry flagEntry(String name, TestBlueprint bp) {
        JobID id = new JobID("farmer", "harvest_wheat");
        return new TestEntry("flag/" + name, id, bp, "flag");
    }

    /**
     * Town-shutdown ritual (ADR-0009, #199): spawn a 2-townie town, begin shutdown, then assert the
     * townies are all absorbed and the flag has gone DORMANT after the minimum-duration floor.
     */
    private static TestBlueprint townShutdownBlueprint() {
        return relocationRitualBase()
                .withPostSpawnAction(beginShutdownAction())
                .withCustomAssertion((level, flagPos, town, output) -> {
                    FlagPhase phase = phaseOf(level, flagPos);
                    long remaining = town.getVillagerHandle().size();
                    output.msg("Flag phase=" + phase + ", townies remaining=" + remaining);
                    return phase == FlagPhase.DORMANT && remaining == 0;
                });
    }

    /**
     * Deed issue + lost-deed recovery (ADR-0009, #199): after shutdown completes, a deed is dropped
     * carrying the flag's reference; discarding it and re-issuing produces another; waking in place
     * returns the flag to ACTIVE. Drives the real deed item + recovery methods end-to-end.
     */
    private static TestBlueprint deedIssueAndRecoverBlueprint() {
        return relocationRitualBase()
                .withPostSpawnAction(beginShutdownAction())
                .withCustomAssertion(TestBlueprintRegistry::assertDeedIssueAndRecover);
    }

    /**
     * Regression guard for the campfire-sleep temp bed destroying the town flag. The bed spot is
     * chosen by {@link CampfireSleepHandler}'s real {@code findSafeSleepPosition} and placed by its
     * real {@code placeTempBed}; before the fix, a solid non-replaceable block (the flag) passed the
     * "clear for sleep" check, so the bed overwrote it and the wake teardown left AIR — the flag,
     * and all its town data, silently vanished. We place a lit campfire two blocks SOUTH of the flag
     * so the first-scanned bed direction (NORTH) puts the bed head exactly on the flag, run the real
     * selection + placement via the autotest seam, then assert the flag block survived.
     */
    private static TestBlueprint campfireSleepPreservesFlagBlueprint() {
        return relocationRitualBase()
                .withPostSpawnAction(TestBlueprintRegistry::campfireSleepOntoFlag)
                .withCustomAssertion((level, flagPos, town, output) -> {
                    boolean flagPresent = phaseOf(level, flagPos) != null;
                    output.msg("Block at flag pos after campfire-sleep = "
                            + level.getBlockState(flagPos).getBlock()
                            + " (flag present=" + flagPresent + ")");
                    return flagPresent;
                });
    }

    // Where the lone door stands relative to the flag: clear of the farm base (x 4..10, z -3..3)
    // so the room scan never attaches it to anything.
    private static final BlockPos DEAD_DOOR_OFFSET = new BlockPos(-2, 0, 0);

    /**
     * Dead-door bubbling (ADR-0011, legibility item 5): a registered door that encloses nothing
     * must surface on the flag as {@link DoorTrouble#NOT_ENCLOSED}, which is what
     * {@code DeadDoorBubbles} reads to draw the door-icon bubble client-side. Drives the real wand
     * path — {@link TownWand#onRightClicked} into its {@code DoorHandler} — rather than calling
     * {@code registerDoor} directly, so a break in the item layer is caught too.
     */
    private static TestBlueprint deadDoorBubbledBlueprint() {
        return relocationRitualBase()
                .withPostSpawnAction(TestBlueprintRegistry::registerLoneDoorViaWand)
                .withCustomAssertion(TestBlueprintRegistry::assertDoorDead);
    }

    private static boolean registerLoneDoorViaWand(
            ServerLevel level,
            BlockPos flagPos,
            TownFlagBlockEntity town,
            TestOutput output
    ) {
        BlockPos doorPos = flagPos.offset(DEAD_DOOR_OFFSET);
        level.setBlockAndUpdate(doorPos.below(), Blocks.DIRT.defaultBlockState());
        BlockState door = Blocks.OAK_DOOR.defaultBlockState();
        level.setBlockAndUpdate(doorPos, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        level.setBlockAndUpdate(doorPos.above(), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));

        // Same wand idiom as ChickenArcTestExecutor: a FakePlayer holding a flag-bound wand,
        // clicking the door's lower half.
        FakePlayer player = FakePlayerFactory.getMinecraft(level);
        player.getInventory().clearContent();
        ItemStack wand = new ItemStack(ItemsInit.TOWN_WAND.get());
        TownFlagBlock.StoreParentOnNBT(wand, flagPos);
        player.setItemInHand(InteractionHand.MAIN_HAND, wand);
        ((TownWand) wand.getItem()).onRightClicked(() -> player, level, doorPos, wand);

        output.msg("Wand-registered lone door at " + doorPos.toShortString()
                + "; deadDoors=" + town.getDeadDoors());
        // A held server should aim at this door, not at a needy townie, so the
        // bubble screenshot shows the door icon and not the (irrelevant) townie
        // need that surfaces when the town outlives the assertion.
        System.setProperty("questown.autotest.aim.door",
                DEAD_DOOR_OFFSET.getX() + "," + DEAD_DOOR_OFFSET.getY() + "," + DEAD_DOOR_OFFSET.getZ());
        return true;
    }

    private static boolean assertDoorDead(
            ServerLevel level,
            BlockPos flagPos,
            TownFlagBlockEntity town,
            TestOutput output
    ) {
        BlockPos doorPos = flagPos.offset(DEAD_DOOR_OFFSET);
        Map<BlockPos, DoorTrouble> dead = town.getDeadDoors();
        output.msg("deadDoors after monitor = " + dead);
        boolean ok = report(output, "1 door-bubbled", dead.containsKey(doorPos));
        ok &= report(output, "2 not-enclosed", dead.get(doorPos) == DoorTrouble.NOT_ENCLOSED);
        // The farm's fence gate must not leak into the door-bubble set.
        ok &= report(output, "3 no-false-positives", dead.size() == 1);
        return ok;
    }

    private static boolean campfireSleepOntoFlag(
            ServerLevel level,
            BlockPos flagPos,
            TownFlagBlockEntity town,
            TestOutput output
    ) {
        // Put the campfire two blocks SOUTH of the flag, so the lane toward the flag
        // (foot = campfire.north(), head = campfire.north().north() = flagPos) can seat a bed
        // whose head lands on the flag. findSafeSleepPosition tries all four directions and takes
        // the first with solid, clear ground, so we must remove the OTHER three lanes' support —
        // otherwise it seats the bed on open ground and never touches the flag (as an earlier run
        // showed: it picked bare ground to the east). With only the flag lane left, the buggy
        // selector is forced onto the flag; the fix makes it decline and leave the flag alone.
        BlockPos campfirePos = flagPos.south().south();
        BlockPos footPos = flagPos.south();
        // The flag lane: supported floor under the foot and the flag, clear air above both.
        level.setBlockAndUpdate(flagPos.below(), Blocks.DIRT.defaultBlockState());
        level.setBlockAndUpdate(footPos.below(), Blocks.DIRT.defaultBlockState());
        level.setBlockAndUpdate(footPos, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(footPos.above(), Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(flagPos.above(), Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(campfirePos.below(), Blocks.DIRT.defaultBlockState());
        level.setBlockAndUpdate(campfirePos,
                Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
        // Collapse the floor under the other three lanes (east / west / south of the campfire) so
        // isSafeToLieOn fails there (unsupported foot), leaving the flag lane as the only candidate.
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos foot = campfirePos.relative(dir);
            if (foot.equals(footPos)) {
                continue;
            }
            level.setBlockAndUpdate(foot.below(), Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(foot.relative(dir).below(), Blocks.AIR.defaultBlockState());
        }
        // The recalled townies idle on the town flag. An entity on a block makes it "obstructed",
        // so findSafeSleepPosition skips it — which hides the bug. In the real incident the flag was
        // empty (the tutorial runs before any villager exists), so nothing blocked the bed from
        // landing on it. Clear the flag lane of entities to reproduce that precondition.
        // Keep the destination inside the harness cleanup zone (origin ±20) so no townie leaks into
        // the next scenario in a full-suite run (shared arena).
        for (net.minecraft.world.entity.LivingEntity e :
                level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                        new AABB(flagPos).inflate(2.5))) {
            e.teleportTo(flagPos.getX() + 14.5, flagPos.getY(), flagPos.getZ() + 14.5);
        }
        BlockPos chosen = CampfireSleepHandler.placeTempBedForTest(level, campfirePos);
        output.msg("Campfire at " + campfirePos.toShortString() + ", flag at " + flagPos.toShortString()
                + ", chosen bed head = " + (chosen == null ? "none" : chosen.toShortString()));
        return true;
    }

    private static TestBlueprint.PostSpawnAction beginShutdownAction() {
        return (level, flagPos, town, output) -> {
            boolean started = town.beginTownShutdown();
            output.msg("beginTownShutdown -> " + started);
            return started;
        };
    }

    private static boolean assertDeedIssueAndRecover(
            ServerLevel level,
            BlockPos flagPos,
            TownFlagBlockEntity town,
            TestOutput output
    ) {
        if (phaseOf(level, flagPos) != FlagPhase.DORMANT) {
            output.msg("Expected DORMANT after shutdown, got " + phaseOf(level, flagPos));
            return false;
        }
        // The deed is no longer dropped as an item; it hovers over the flag and is collected on
        // interaction (ADR-0009). Assert it became available on shutdown completion.
        if (!town.isDeedAvailable()) {
            output.msg("No deed became available on the flag after shutdown completion");
            return false;
        }
        output.msg("Deed available on the flag (hover + collect-on-interact)");
        // Re-issue (loss-protection) must keep a deed available while DORMANT.
        if (!town.reissueDeed() || !town.isDeedAvailable()) {
            output.msg("Re-issue did not keep a deed available for this flag");
            return false;
        }
        boolean woke = town.wakeInPlace();
        FlagPhase after = phaseOf(level, flagPos);
        // Roster re-spawn is queued (SpawnVisitorReward, same path the harness uses elsewhere) and
        // completes on later ticks, so we assert the synchronous wake outcome: ACTIVE again.
        output.msg("wakeInPlace=" + woke + ", phase now=" + after);
        // Waking must retract the waiting deed, or a live town keeps handing out references to itself
        // and the deed branch shadows the flag menu forever.
        if (town.isDeedAvailable()) {
            output.msg("Deed still available on the flag after waking the town in place");
            return false;
        }
        return woke && after == FlagPhase.ACTIVE;
    }

    /**
     * Town relocation placement (ADR-0009, #199, Phase 3): isolate the placement step from the
     * shutdown ritual (which has its own test). After the town spawns, drop it straight to DORMANT
     * (a blockstate write, not the logic under test), mint a deed, and drive the real
     * {@link TownRelocation#place} to a nearby target with a non-zero Y delta. The realtime monitor
     * then ticks the new flag so its data hydrates, its roster respawns, and its rooms reconstitute,
     * which the assertion verifies end-to-end.
     */
    private static TestBlueprint relocateNearbyBlueprint() {
        RelocateCapture cap = new RelocateCapture();
        return relocationRitualBase()
                .withPostSpawnAction((level, flagPos, town, output) -> relocatePostSpawn(level, flagPos, town, output, cap))
                .withCustomAssertion((level, flagPos, town, output) -> assertRelocateNearby(level, flagPos, output, cap));
    }

    // Carries pre-relocation state from the post-spawn trigger to the end-state assertion (the two
    // run in different phases, so we close over this holder rather than re-deriving).
    private static final class RelocateCapture {
        UUID originalUuid;
        int oldFlagY;
        BlockPos targetPos;
        Set<TownPosition> originalDoors = Set.of();
        Set<String> originalAbsDoors = Set.of();
        long rosterSize;
        CompoundTag preData = new CompoundTag();
        boolean placed;
        // Phase 4: how out-of-range fixtures are handled, and the cutoff that decides "out of range".
        // Defaults reproduce the Phase-3 nearby behaviour (carry everything, real tick radius).
        FarFixturePolicy policy = FarFixturePolicy.BRING_ALL;
        long tickRadiusSq = Config.TOWN_TICK_RADIUS.get();
        int beyondCount;
        // Job proficiency (ADR-0010): a known level stamped on one townie pre-move, asserted to
        // survive the whole-blob copy (it rides NBT_TOWN_STATE, no bespoke relocation code).
        UUID profVillager;
        float expectedProf;
        static final String PROF_ID = "smithing";
    }

    private static boolean relocatePostSpawn(
            ServerLevel level,
            BlockPos flagPos,
            TownFlagBlockEntity town,
            TestOutput output,
            RelocateCapture cap
    ) {
        cap.originalUuid = town.getUUID();
        cap.oldFlagY = flagPos.getY();
        // Nearby (within tick radius, so no far-away path) with a deliberate +1 Y delta so the
        // fixture re-anchor (scanLevel) is genuinely exercised; inside the farm footprint so the
        // new flag sits over solid ground.
        cap.targetPos = flagPos.offset(5, 1, 0);
        Set<TownPosition> fixtures = allFixtures(town);
        cap.originalDoors = new HashSet<>(fixtures);
        cap.originalAbsDoors = absoluteKeys(fixtures, cap.oldFlagY);
        cap.rosterSize = town.getVillagerHandle().size();
        cap.beyondCount = TownRelocation
                .fixturesBeyondRadius(cap.targetPos, cap.oldFlagY, fixtures, cap.tickRadiusSq)
                .size();
        // Put the onboarding arc mid-flight (non-default beat + rotation) so the carry-check below
        // proves the chicken beat state and structure rotation ride the whole-blob copy to the new
        // flag — #199 Phase 5 "the tutorial follows you". Re-anchoring the beat geometry itself is
        // locked deterministically by HelperChickenBeatRelocationTest.
        town.setChickenBeatState(ca.bradj.questown.mobs.helperchicken.ChickenBeatState.WAITING_FOR_CHEST);
        town.setChickenStructureRotation(net.minecraft.world.level.block.Rotation.CLOCKWISE_90);
        // Stamp a known proficiency on the first townie so the assertion can prove it rode the
        // whole-blob copy (ADR-0010 Phase 5). Set before writeTownData so it lands in NBT_TOWN_STATE.
        java.util.Collection<net.minecraft.world.entity.LivingEntity> ents = town.getVillagerHandle().entities();
        if (!ents.isEmpty()) {
            cap.profVillager = ents.iterator().next().getUUID();
            cap.expectedProf = 0.42f;
            town.getVillagerHandle().setProficiency(cap.profVillager, RelocateCapture.PROF_ID, cap.expectedProf);
        }
        town.writeTownData(cap.preData);
        output.msg("pre-relocation: uuid=" + cap.originalUuid + " oldFlagY=" + cap.oldFlagY
                + " fixtures=" + fixtures.size() + " beyond=" + cap.beyondCount
                + " roster=" + cap.rosterSize);

        // Reach the dormant precondition without re-running the shutdown floor/recall (covered by
        // flag/town_shutdown): flip the phase directly, then mint the deed exactly as completion does.
        setPhase(level, flagPos, FlagPhase.DORMANT);
        ItemStack deed = RelocationDeedItem.forReference(
                cap.originalUuid, flagPos, level.dimension().location()
        );
        RelocationResult result = TownRelocation.place(
                level, deed, cap.targetPos, cap.policy, cap.tickRadiusSq
        );
        output.msg("place(" + cap.targetPos + ", " + cap.policy + ", rSq=" + cap.tickRadiusSq
                + ") -> " + result);
        cap.placed = result == RelocationResult.OK;
        return cap.placed;
    }

    private static boolean assertRelocateNearby(
            ServerLevel level,
            BlockPos originalFlagPos,
            TestOutput output,
            RelocateCapture cap
    ) {
        if (!cap.placed) {
            output.msg("FAIL precondition: relocation did not place");
            return false;
        }
        boolean ok = true;

        // (1) original flag destroyed
        boolean originalGone = TownFlagBlockEntity.getFromPos(level, originalFlagPos) == null;
        ok &= report(output, "1 original-destroyed", originalGone);

        TownFlagBlockEntity newBe = TownFlagBlockEntity.getFromPos(level, cap.targetPos);
        if (newBe == null) {
            report(output, "2 new-flag-present", false);
            return false;
        }
        BlockState ns = level.getBlockState(cap.targetPos);
        FlagPhase phase = ns.hasProperty(TownFlagBlock.PHASE) ? ns.getValue(TownFlagBlock.PHASE) : null;
        boolean inactive = ns.hasProperty(TownFlagBlock.INACTIVE) && ns.getValue(TownFlagBlock.INACTIVE);
        output.msg("new flag phase=" + phase + " inactive=" + inactive);
        // (2) new flag ACTIVE and not INACTIVE
        ok &= report(output, "2 new-flag-active-not-inactive", phase == FlagPhase.ACTIVE && !inactive);

        // (3) identity carried
        ok &= report(output, "3 identity-carried", cap.originalUuid.equals(newBe.getUUID()));

        // (4) fixtures re-anchored: absolute positions preserved, scanLevel changed (Y moved)
        Set<TownPosition> newDoors = allFixtures(newBe);
        if (newDoors.isEmpty()) {
            ok &= report(output, "4 fixtures-rebased (NO FIXTURES — cannot verify)", false);
        } else {
            Set<String> newAbs = absoluteKeys(newDoors, cap.targetPos.getY());
            boolean absPreserved = newAbs.equals(cap.originalAbsDoors);
            boolean scanChanged = !newDoors.equals(cap.originalDoors);
            output.msg("fixtures: newAbs=" + newAbs + " origAbs=" + cap.originalAbsDoors);
            ok &= report(output, "4 fixtures-abs-preserved", absPreserved);
            ok &= report(output, "4 fixtures-scanLevel-rebased", scanChanged);
        }

        // (6) roster respawned to the carried count
        long live = newBe.getVillagerHandle().size();
        output.msg("respawned roster=" + live + " (expected " + cap.rosterSize + ")");
        ok &= report(output, "6 roster-respawned", live == cap.rosterSize);

        // (7) rooms reconstituted from the carried doors
        int rooms = newBe.getRoomHandle().getMatches(x -> true).size();
        output.msg("rooms reconstituted=" + rooms);
        ok &= report(output, "7 rooms-reconstituted", rooms >= 1);

        // (8) intangible town data carried wholesale (knowledge + economics round-trip identically)
        CompoundTag postData = new CompoundTag();
        newBe.writeTownData(postData);
        for (String key : CARRIED_DATA_KEYS) {
            boolean same = Objects.equals(cap.preData.get(key), postData.get(key));
            ok &= report(output, "8 data-carried[" + key + "]", same);
        }

        // (9) job proficiency rode the whole-blob copy: the stamped townie keeps its level (ADR-0010)
        if (cap.profVillager != null) {
            float got = newBe.getVillagerHandle().getProficiency(cap.profVillager, RelocateCapture.PROF_ID);
            output.msg("proficiency: got=" + got + " expected=" + cap.expectedProf);
            ok &= report(output, "9 proficiency-carried", Math.abs(got - cap.expectedProf) < 1e-4f);
        } else {
            ok &= report(output, "9 proficiency-carried (NO ROSTER — cannot verify)", false);
        }

        return ok;
    }

    /**
     * Deed consumption on a successful placement (#199, playtest follow-up). A deed that survives its
     * own relocation is a duplication hazard: placed again it mints a second flag referencing a town
     * that no longer exists. The other relocate scenarios call {@link TownRelocation#place} directly,
     * so nothing covered the item layer that is supposed to take the deed away.
     *
     * <p>This drives the whole real use path — {@code ServerPlayerGameMode.useItemOn} into
     * {@link RelocationDeedItem#useOn} — with a <em>creative</em> player, because creative is exactly
     * where the naive {@code shrink(1)} fails silently: the game mode restores the stack's count after
     * the use and hands the deed straight back.
     */
    private static TestBlueprint deedConsumedOnPlaceBlueprint() {
        DeedUseCapture cap = new DeedUseCapture();
        return relocationRitualBase()
                .withPostSpawnAction((level, flagPos, town, output) -> placeDeedByHand(level, flagPos, town, output, cap))
                .withCustomAssertion((level, flagPos, town, output) -> assertDeedConsumed(level, flagPos, output, cap));
    }

    // Carries the item-layer outcome from the post-spawn trigger to the end-state assertion. The
    // counts are read immediately around the use, since the monitor phase that follows would let a
    // dropped deed despawn or be picked up and hide the leak.
    private static final class DeedUseCapture {
        BlockPos targetPos;
        InteractionResult useResult;
        int deedsBefore;
        int deedsAfter;
        boolean relocated;
    }

    private static boolean placeDeedByHand(
            ServerLevel level,
            BlockPos flagPos,
            TownFlagBlockEntity town,
            TestOutput output,
            DeedUseCapture cap
    ) {
        // Click the farm floor, so the deed lands the new flag on the air block above it — the same
        // "one block off the clicked face" idiom useOn implements.
        BlockPos clickedPos = flagPos.offset(5, -1, 0);
        cap.targetPos = clickedPos.above();

        // Dormant is a precondition of placement, not the thing under test (flag/town_shutdown owns
        // the ritual), so reach it with a blockstate write and mint the deed as completion does.
        setPhase(level, flagPos, FlagPhase.DORMANT);
        ItemStack deed = RelocationDeedItem.forReference(
                town.getUUID(), flagPos, level.dimension().location()
        );

        ServerPlayer player = creativeDeedHolder(level, clickedPos, deed);
        cap.deedsBefore = countDeeds(player);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(clickedPos), Direction.UP, clickedPos, false);
        cap.useResult = player.gameMode.useItemOn(
                player,
                level,
                player.getItemInHand(InteractionHand.MAIN_HAND),
                InteractionHand.MAIN_HAND,
                hit
        );
        cap.deedsAfter = countDeeds(player);
        cap.relocated = TownFlagBlockEntity.getFromPos(level, cap.targetPos) != null;
        output.msg("useItemOn(" + cap.targetPos.toShortString() + ") -> " + cap.useResult
                + ", deeds held " + cap.deedsBefore + " -> " + cap.deedsAfter
                + ", new flag present=" + cap.relocated);
        // Report through the assertion rather than failing here, so the counts always reach the log.
        return true;
    }

    // A creative player holding nothing but the deed, standing on the block it will click. Forge's
    // FakePlayer carries a no-op net handler, so the game mode's client packets go nowhere and the
    // creative branch (which is what makes this scenario worth running) executes for real.
    private static ServerPlayer creativeDeedHolder(
            ServerLevel level,
            BlockPos stand,
            ItemStack deed
    ) {
        FakePlayer player = FakePlayerFactory.getMinecraft(level);
        player.getInventory().clearContent();
        player.setGameMode(GameType.CREATIVE);
        player.moveTo(stand.getX() + 0.5, stand.getY() + 1.0, stand.getZ() + 0.5);
        player.setItemInHand(InteractionHand.MAIN_HAND, deed);
        return player;
    }

    private static int countDeeds(ServerPlayer player) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (RelocationDeedItem.isDeed(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static long deedsOnTheGround(
            ServerLevel level,
            BlockPos around
    ) {
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(around).inflate(20))
                .stream()
                .filter(e -> RelocationDeedItem.isDeed(e.getItem()))
                .count();
    }

    private static boolean assertDeedConsumed(
            ServerLevel level,
            BlockPos originalFlagPos,
            TestOutput output,
            DeedUseCapture cap
    ) {
        boolean ok = true;
        ok &= report(output, "1 relocation-placed", cap.relocated);
        ok &= report(output, "2 original-destroyed",
                TownFlagBlockEntity.getFromPos(level, originalFlagPos) == null);
        ok &= report(output, "3 use-consumed-action",
                cap.useResult != null && cap.useResult.consumesAction());
        ok &= report(output, "4 deed-held-before", cap.deedsBefore == 1);
        // The dupe hazard itself: a deed that outlives its own placement can mint a second flag.
        ok &= report(output, "5 deed-gone-after", cap.deedsAfter == 0);
        long dropped = deedsOnTheGround(level, originalFlagPos);
        output.msg("deed item entities near the old flag = " + dropped);
        ok &= report(output, "6 no-deed-dropped", dropped == 0);
        return ok;
    }

    /**
     * Town relocation "leave it behind" (ADR-0009, #199 Phase 4): relocate with {@link
     * FarFixturePolicy#LEAVE_BEHIND} and a deliberately tiny tick radius, so every fixture in the
     * arena counts as "too far to come" against the nearby (solid-ground) target. Drives the real
     * {@link TownRelocation#place} overload — no far terrain needed, so the scenario stays in the
     * shared arena and leaves no residue. The assertion proves the far fixtures are dropped while the
     * intangibles (roster, knowledge) still carry. The "bring it anyway" path just skips the drop, so
     * it's covered by {@code relocate_nearby} plus the {@code fixturesBeyondRadius} JUnit.
     */
    private static TestBlueprint relocateFarBlueprint() {
        RelocateCapture cap = new RelocateCapture();
        cap.policy = FarFixturePolicy.LEAVE_BEHIND;
        cap.tickRadiusSq = 1; // 1-block radius: anything past the target's own block is "far"
        return relocationRitualBase()
                .withPostSpawnAction((level, flagPos, town, output) -> relocatePostSpawn(level, flagPos, town, output, cap))
                .withCustomAssertion((level, flagPos, town, output) -> assertRelocateFar(level, flagPos, output, cap));
    }

    private static boolean assertRelocateFar(
            ServerLevel level,
            BlockPos originalFlagPos,
            TestOutput output,
            RelocateCapture cap
    ) {
        if (!cap.placed) {
            output.msg("FAIL precondition: relocation did not place");
            return false;
        }
        boolean ok = true;

        // (0) the choice was meaningful: with the tiny radius, every fixture was out of range
        output.msg("fixtures=" + cap.originalDoors.size() + " beyond=" + cap.beyondCount);
        ok &= report(output, "0 all-fixtures-were-far",
                cap.beyondCount > 0 && cap.beyondCount == cap.originalDoors.size());

        // (1) original flag destroyed
        ok &= report(output, "1 original-destroyed",
                TownFlagBlockEntity.getFromPos(level, originalFlagPos) == null);

        TownFlagBlockEntity newBe = TownFlagBlockEntity.getFromPos(level, cap.targetPos);
        if (newBe == null) {
            report(output, "2 new-flag-present", false);
            return false;
        }
        BlockState ns = level.getBlockState(cap.targetPos);
        FlagPhase phase = ns.hasProperty(TownFlagBlock.PHASE) ? ns.getValue(TownFlagBlock.PHASE) : null;
        boolean inactive = ns.hasProperty(TownFlagBlock.INACTIVE) && ns.getValue(TownFlagBlock.INACTIVE);
        // (2) new flag ACTIVE and not INACTIVE
        ok &= report(output, "2 new-flag-active-not-inactive", phase == FlagPhase.ACTIVE && !inactive);

        // (3) identity carried even though the body of the town was left behind
        ok &= report(output, "3 identity-carried", cap.originalUuid.equals(newBe.getUUID()));

        // (4) the far fixtures were dropped — none carried to the new flag
        Set<TownPosition> newDoors = allFixtures(newBe);
        output.msg("relocated fixtures=" + newDoors.size() + " (expected 0 — all left behind)");
        ok &= report(output, "4 far-fixtures-dropped", newDoors.isEmpty());

        // (5) intangibles still carry: the roster respawns even with no rooms to live in
        long live = newBe.getVillagerHandle().size();
        output.msg("respawned roster=" + live + " (expected " + cap.rosterSize + ")");
        ok &= report(output, "5 roster-carried", live == cap.rosterSize);

        // (6) intangible town data (knowledge etc.) carried wholesale
        CompoundTag postData = new CompoundTag();
        newBe.writeTownData(postData);
        for (String key : CARRIED_DATA_KEYS) {
            ok &= report(output, "6 data-carried[" + key + "]",
                    Objects.equals(cap.preData.get(key), postData.get(key)));
        }

        return ok;
    }

    // Stable, persisted town data copied wholesale by relocation; if these round-trip identically
    // across the move, the rest of the whole-blob copy did too (ADR-0009 copy fidelity). Economics
    // is intentionally excluded: it's a running log of unmet-needs records (with ticks) that legit-
    // imately grows over the monitored ticks, so equality is not a valid carry-check for it.
    private static final java.util.List<String> CARRIED_DATA_KEYS = java.util.List.of(
            Questown.MODID + "_knowledge",
            Questown.MODID + "_bops_stored",
            Questown.MODID + "_bonus_given",
            // Onboarding arc state (relocatePostSpawn sets these non-default before the move):
            // beat + rotation must survive so the helper chicken re-derives its beats at the new flag.
            Questown.MODID + "_chicken_beat_state",
            Questown.MODID + "_chicken_structure_rotation"
    );

    // The room entrance may be registered as a door or a fence gate; relocation re-anchors both, so
    // the fixture check spans the union.
    private static Set<TownPosition> allFixtures(TownFlagBlockEntity flag) {
        Set<TownPosition> all = new HashSet<>(flag.getRoomHandle().getAllRegisteredDoors());
        if (flag.getRoomHandle() instanceof TownRoomsHandle rh) {
            all.addAll(rh.getRegisteredRooms().getRegisteredGates());
        }
        return all;
    }

    private static Set<String> absoluteKeys(Set<TownPosition> fixtures, int flagY) {
        return fixtures.stream()
                .map(p -> p.x + "," + (flagY + p.scanLevel) + "," + p.z)
                .collect(Collectors.toSet());
    }

    private static boolean report(TestOutput output, String label, boolean pass) {
        output.msg((pass ? "PASS " : "FAIL ") + label);
        return pass;
    }

    private static void setPhase(ServerLevel level, BlockPos flagPos, FlagPhase phase) {
        BlockState s = level.getBlockState(flagPos);
        if (s.hasProperty(TownFlagBlock.PHASE)) {
            level.setBlockAndUpdate(flagPos, s.setValue(TownFlagBlock.PHASE, phase));
        }
    }

    private static FlagPhase phaseOf(ServerLevel level, BlockPos flagPos) {
        BlockState bs = level.getBlockState(flagPos);
        return bs.hasProperty(TownFlagBlock.PHASE) ? bs.getValue(TownFlagBlock.PHASE) : null;
    }

    private static List<ItemEntity> findDeeds(ServerLevel level, BlockPos flagPos) {
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(flagPos).inflate(6.0))
                    .stream()
                    .filter(e -> RelocationDeedItem.isDeed(e.getItem()))
                    .toList();
    }

    private static List<ItemEntity> deedsForFlag(
            ServerLevel level,
            BlockPos flagPos,
            TownFlagBlockEntity town
    ) {
        return findDeeds(level, flagPos).stream()
                .filter(e -> referencesFlag(e.getItem(), level, flagPos, town))
                .toList();
    }

    private static boolean referencesFlag(
            ItemStack deed,
            ServerLevel level,
            BlockPos flagPos,
            TownFlagBlockEntity town
    ) {
        return town.getUUID().equals(RelocationDeedItem.getTownUuid(deed))
                && flagPos.equals(RelocationDeedItem.getFlagPos(deed))
                && level.dimension().location().equals(RelocationDeedItem.getDimension(deed));
    }

    /**
     * The shared arena for relocation-ritual scenarios: a fenced 2-townie town, realtime-only
     * (skipWarp, since the ritual is driven by the realtime ticker, never warp), monitored long
     * enough to clear the 200-tick shutdown floor. Callers attach the trigger + assertion.
     */
    private static TestBlueprint relocationRitualBase() {
        List<BlockPlacement> blocks = new ArrayList<>();
        int ox = 4;
        int oz = -3;
        for (int x = 0; x < 7; x++) {
            for (int z = 0; z < 7; z++) {
                boolean isEdge = x == 0 || x == 6 || z == 0 || z == 6;
                BlockPos offset = new BlockPos(ox + x, 0, oz + z);
                if (isEdge) {
                    if (x == 3 && z == 6) {
                        blocks.add(new BlockPlacement(offset, Blocks.OAK_FENCE_GATE.defaultBlockState()));
                    } else {
                        blocks.add(new BlockPlacement(offset, Blocks.OAK_FENCE.defaultBlockState()));
                    }
                } else {
                    blocks.add(new BlockPlacement(offset.below(), Blocks.DIRT.defaultBlockState()));
                }
            }
        }
        BlockPos chestOffset = new BlockPos(ox + 1, 0, oz + 5);
        BlockPos gateOffset = new BlockPos(ox + 3, 0, oz + 6);

        TestExpectation noProducts = new TestExpectation(List.of(), 0, 0);

        return new TestBlueprint(
                RoomType.FARM,
                blocks,
                List.of(),   // no supplies — townies don't work, they're recalled
                gateOffset,
                chestOffset,
                SpecialQuests.FARM,
                noProducts,
                null,        // supplyDoorOffset
                null,        // warpAmountOverride
                null,        // startTimeTick
                2,           // villagerCount
                true,        // realtimePhase
                300,         // realtimeTicks — comfortably past the 200-tick shutdown floor
                false,       // drainHungerBeforeTest
                true,        // skipWarp — ritual runs on the realtime path only
                null,        // realtimeExpectation
                null,        // minExpectedFullnessAfter
                null,        // extraBlockRoomOffset
                null,        // extraBlockRoomId
                null,        // expectedVillagerHeld
                false        // useNaturalWarp
        );
    }

    private static TestBlueprint farmerBlueprint() {
        List<BlockPlacement> blocks = new ArrayList<>();

        int ox = 4;
        int oz = -3;

        for (int x = 0; x < 7; x++) {
            for (int z = 0; z < 7; z++) {
                boolean isEdge = x == 0 || x == 6 || z == 0 || z == 6;
                BlockPos offset = new BlockPos(ox + x, 0, oz + z);
                if (isEdge) {
                    if (x == 3 && z == 6) {
                        blocks.add(new BlockPlacement(offset, Blocks.OAK_FENCE_GATE.defaultBlockState()));
                    } else {
                        blocks.add(new BlockPlacement(offset, Blocks.OAK_FENCE.defaultBlockState()));
                    }
                } else {
                    blocks.add(new BlockPlacement(offset.below(), Blocks.FARMLAND.defaultBlockState()));
                    blocks.add(new BlockPlacement(offset, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7)));
                }
            }
        }

        BlockPos composterOffset = new BlockPos(ox + 2, 0, oz + 2);
        blocks.removeIf(bp -> bp.offset().equals(composterOffset));
        blocks.add(new BlockPlacement(composterOffset, Blocks.COMPOSTER.defaultBlockState()));

        BlockPos chestOffset = new BlockPos(ox + 1, 0, oz + 5);
        blocks.removeIf(bp -> bp.offset().equals(chestOffset));
        blocks.add(new BlockPlacement(chestOffset.below(), Blocks.DIRT.defaultBlockState()));

        BlockPos gateOffset = new BlockPos(ox + 3, 0, oz + 6);

        List<ItemStack> supplies = List.of(
                new ItemStack(Items.WOODEN_HOE, 1),
                new ItemStack(Items.WHEAT_SEEDS, 32)
        );

        TestExpectation expectation = new TestExpectation(
                List.of(new ExpectedProduct("minecraft:wheat", 1, null)),
                1,
                100
        );

        return new TestBlueprint(
                RoomType.FARM,
                blocks,
                supplies,
                gateOffset,
                chestOffset,
                SpecialQuests.FARM,
                expectation
        );
    }

    /**
     * Proficiency leveling parity (ADR-0010, #269) — the feature's acceptance gate. The same work
     * must move a townie's proficiency by the same amount whether it happened live or offline; a
     * warp path that credited "once per important tick" instead of once per completed action would
     * drift a townie's skill away from its realtime self while the player was away.
     * <p>
     * Runs both passes against the <em>same</em> villager: realtime first (the executor's monitored
     * window), then an explicit warp inside the assertion. The executor's own warp pass can't be
     * used — it kills and respawns the roster before the realtime phase, so the two halves would
     * describe different townies with different UUIDs.
     * <p>
     * Farmer harvest is the vehicle because it is a single work state (`work: 10`,
     * `cooldown_ticks: 10`) driven by {@code decrWork}, so one completed action is one harvest.
     */
    private static TestBlueprint proficiencyParityBlueprint() {
        TestBlueprint base = farmerBlueprint();
        ParityCapture cap = new ParityCapture();
        // A second hoe. Warp scores a job's tools against the town's containers, and the base
        // blueprint's only hoe ends up in the villager's hands — leaving warp to conclude the
        // harvest job is unequipped and re-score onto compost, completing zero actions under test.
        List<ItemStack> supplies = new ArrayList<>(base.supplyItems());
        supplies.add(new ItemStack(Items.WOODEN_HOE, 1));
        return new TestBlueprint(
                base.roomType(), base.blocks(), supplies,
                base.doorOrGateOffset(), base.chestOffset(), base.roomId(),
                base.expectation(),
                base.supplyDoorOffset(),
                null,        // warpAmountOverride — the warp half is driven by the assertion
                0L,          // startTimeTick — start at dawn so the realtime window is productive
                1,           // villagerCount — one worker, so the action counter is unambiguous
                true,        // realtimePhase
                4800,        // realtimeTicks
                false,       // drainHungerBeforeTest
                true,        // skipWarp — this scenario gates on the realtime pass (see getWarpPassed)
                null, null, null, null, null, false
        )
                .withPostSpawnAction((level, flagPos, town, output) -> parityPostSpawn(town, cap, output))
                .withCustomAssertion((level, flagPos, town, output) -> assertLevelingParity(level, flagPos, town, cap, output));
    }

    /**
     * Cross-phase holder for the parity scenario: the post-spawn action stamps the baseline, the
     * custom assertion reads it back after the realtime window (the {@code RelocateCapture} idiom).
     */
    private static final class ParityCapture {
        // Two ids so the assertion can check the neglected-proficiency decay as well as the gain.
        static final String WORKED = "farming";
        static final String NEGLECTED = "smithing";
        static final JobID JOB = new JobID("farmer", "harvest_wheat");
        // Mid-band baselines: far enough from the [0,1] clamps that a whole window of gain or
        // decay cannot hit one, which would flatten the delta and mask a parity break.
        static final float BASE_WORKED = 0.3f;
        static final float BASE_NEGLECTED = 0.5f;
        // Warp compresses a window into a handful of simulated steps (~1 per 480 ticks), so the
        // budget has to be generous to complete a meaningful number of actions — but small enough
        // that the gain cannot reach the level-1 clamp, which would flatten the delta and fake a
        // mismatch. ~40 actions of headroom sits between those bounds.
        static final int WARP_TICKS = 48000;

        @Nullable UUID villager;
    }

    /**
     * The acceptance gate of ADR-0010: per completed work action, the realtime path and the warp
     * path must move proficiency by the same amount — gain on the worked id, decay on the others.
     * Normalizing by completed actions (rather than comparing raw deltas) is what makes the two
     * windows comparable without forcing them to do an identical amount of work.
     */
    private static boolean assertLevelingParity(
            ServerLevel level,
            BlockPos flagPos,
            TownFlagBlockEntity town,
            ParityCapture cap,
            TestOutput output
    ) {
        try {
            if (cap.villager == null) {
                return report(output, "parity (no villager was captured)", false);
            }
            VillagerHolder handle = town.getVillagerHandle();
            long realtimeActions = AbstractWorldInteraction.getProficiencyBearingActionsForTest();
            float rtGain = handle.getProficiency(cap.villager, ParityCapture.WORKED) - ParityCapture.BASE_WORKED;
            float rtDecay = ParityCapture.BASE_NEGLECTED - handle.getProficiency(cap.villager, ParityCapture.NEGLECTED);
            output.msg("parity realtime: actions=" + realtimeActions + " gain=" + rtGain + " decay=" + rtDecay);

            // Restart the warp window from the same baseline, so both windows are measured under
            // identical conditions and neither inherits the other's accumulated level.
            handle.setProficiencies(cap.villager, Map.of(
                    ParityCapture.WORKED, ParityCapture.BASE_WORKED,
                    ParityCapture.NEGLECTED, ParityCapture.BASE_NEGLECTED
            ));
            AbstractWorldInteraction.resetProficiencyBearingActionsForTest();
            // The realtime window stripped the field, and a farmer with nothing to harvest re-scores
            // onto compost/bone_meal — different jobs, so the warp window would complete zero actions
            // of the job under test. Regrow the crop so both windows start from the same full field.
            int regrown = regrowHarvestableCrops(level, flagPos);
            output.msg("parity: regrew " + regrown + " crops before the warp window");
            // Warp advances the tile's stored state, so the reset baseline has to be published or
            // the warp half would run against the level the realtime half left behind.
            town.publishStateToTileForTest();
            MCTownState warped = town.warpTime(ParityCapture.WARP_TICKS);
            long warpActions = AbstractWorldInteraction.getProficiencyBearingActionsForTest();
            if (warped == null) {
                return report(output, "parity (warp produced no town state)", false);
            }
            float warpWorked = proficiencyIn(warped, cap.villager, ParityCapture.WORKED);
            float warpNeglected = proficiencyIn(warped, cap.villager, ParityCapture.NEGLECTED);
            float wpGain = warpWorked - ParityCapture.BASE_WORKED;
            float wpDecay = ParityCapture.BASE_NEGLECTED - warpNeglected;
            output.msg("parity warp: actions=" + warpActions + " gain=" + wpGain + " decay=" + wpDecay);

            boolean ok = report(output, "parity realtime did work", realtimeActions > 0);
            ok &= report(output, "parity warp did work", warpActions > 0);
            if (!ok) {
                // Zero work on either side makes every ratio below vacuously equal — the one way
                // this gate could go green while proving nothing.
                return false;
            }
            ok &= report(output, "parity worked level did not hit the clamp (shorten the window)",
                         warpWorked < 1f && handle.getProficiency(cap.villager, ParityCapture.WORKED) <= 1f);
            ok &= report(output, "parity neglected level did not hit the floor (shorten the window)",
                         warpNeglected > 0f);

            float rtGainPer = rtGain / realtimeActions;
            float wpGainPer = wpGain / warpActions;
            float rtDecayPer = rtDecay / realtimeActions;
            float wpDecayPer = wpDecay / warpActions;
            output.msg("parity per-action gain: realtime=" + rtGainPer + " warp=" + wpGainPer);
            output.msg("parity per-action decay: realtime=" + rtDecayPer + " warp=" + wpDecayPer);
            ok &= report(output, "parity gain per action matches", Math.abs(rtGainPer - wpGainPer) < 1e-4f);
            ok &= report(output, "parity decay per action matches", Math.abs(rtDecayPer - wpDecayPer) < 1e-5f);
            return ok;
        } finally {
            ServerJobsRegistry.clearProficiencyIdOverridesForTest();
        }
    }

    /**
     * Return every wheat block around the flag to fully grown, so a second measurement window sees
     * the same harvestable field the first one did.
     */
    private static int regrowHarvestableCrops(
            ServerLevel level,
            BlockPos flagPos
    ) {
        int regrown = 0;
        for (int x = -12; x <= 12; x++) {
            for (int z = -12; z <= 12; z++) {
                for (int y = -2; y <= 2; y++) {
                    BlockPos pos = flagPos.offset(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (!state.is(Blocks.WHEAT) || !state.hasProperty(CropBlock.AGE)) {
                        continue;
                    }
                    if (state.getValue(CropBlock.AGE) == 7) {
                        continue;
                    }
                    level.setBlock(pos, state.setValue(CropBlock.AGE, 7), Block.UPDATE_ALL);
                    regrown++;
                }
            }
        }
        return regrown;
    }

    private static float proficiencyIn(
            MCTownState state,
            UUID villager,
            String proficiencyId
    ) {
        for (TownState.VillagerData<MCHeldItem> v : state.villagers) {
            if (villager.equals(v.uuid)) {
                return v.getProficiencyLevel(proficiencyId);
            }
        }
        return Float.NaN;
    }

    private static boolean parityPostSpawn(
            TownFlagBlockEntity town,
            ParityCapture cap,
            TestOutput output
    ) {
        Collection<net.minecraft.world.entity.LivingEntity> ents = town.getVillagerHandle().entities();
        if (ents.isEmpty()) {
            output.msg("No villager to measure proficiency on");
            return false;
        }
        cap.villager = ents.iterator().next().getUUID();
        // Shipped job JSON declares no proficiency-id (the feature is inert), so the job under test
        // is given one for the duration of this scenario. Cleared in the assertion's finally block.
        ServerJobsRegistry.overrideProficiencyIdForTest(ParityCapture.JOB, ParityCapture.WORKED);
        town.getVillagerHandle().setProficiencies(cap.villager, Map.of(
                ParityCapture.WORKED, ParityCapture.BASE_WORKED,
                ParityCapture.NEGLECTED, ParityCapture.BASE_NEGLECTED
        ));
        AbstractWorldInteraction.resetProficiencyBearingActionsForTest();
        output.msg("parity: baseline " + ParityCapture.WORKED + "=" + ParityCapture.BASE_WORKED
                + " " + ParityCapture.NEGLECTED + "=" + ParityCapture.BASE_NEGLECTED);
        return true;
    }

    /**
     * Fenced farm plot containing a bare 4-high oak_log column with a reachable base
     * (no leaves; deterministic 4-log drop count). The arborist is in-town work, so the
     * chop runs during warp and deposits oak_logs in the chest.
     * <p>
     * Warp-only (realtimePhase=false): the chopped column is a non-renewable resource within
     * the arena, so a post-warp realtime re-run would have no tree left to chop. Realtime/warp
     * parity for the chop seam is structurally guaranteed (both paths share
     * {@code event.world().chopTree(...)}); the full plant→grow→chop cycle is exercised by the
     * full-cycle blueprint.
     */
    private static TestBlueprint arboristCutTreesBlueprint() {
        List<BlockPlacement> blocks = new ArrayList<>();

        int ox = 4;
        int oz = -3;

        for (int x = 0; x < 7; x++) {
            for (int z = 0; z < 7; z++) {
                boolean isEdge = x == 0 || x == 6 || z == 0 || z == 6;
                BlockPos offset = new BlockPos(ox + x, 0, oz + z);
                if (isEdge) {
                    if (x == 3 && z == 6) {
                        blocks.add(new BlockPlacement(offset, Blocks.OAK_FENCE_GATE.defaultBlockState()));
                    } else {
                        blocks.add(new BlockPlacement(offset, Blocks.OAK_FENCE.defaultBlockState()));
                    }
                } else {
                    blocks.add(new BlockPlacement(offset.below(), Blocks.DIRT.defaultBlockState()));
                }
            }
        }

        // A bare 4-high oak_log column at an interior cell, base reachable from the floor.
        BlockPos trunkBase = new BlockPos(ox + 3, 0, oz + 3);
        for (int y = 0; y < 4; y++) {
            blocks.add(new BlockPlacement(trunkBase.above(y), Blocks.OAK_LOG.defaultBlockState()));
        }

        BlockPos chestOffset = new BlockPos(ox + 1, 0, oz + 5);
        blocks.add(new BlockPlacement(chestOffset.below(), Blocks.DIRT.defaultBlockState()));

        BlockPos gateOffset = new BlockPos(ox + 3, 0, oz + 6);

        List<ItemStack> supplies = List.of(
                new ItemStack(Items.WOODEN_AXE, 1)
        );

        TestExpectation expectation = new TestExpectation(
                List.of(new ExpectedProduct("minecraft:oak_log", 4, null)),
                1,
                100
        );

        return new TestBlueprint(
                RoomType.FARM,
                blocks,
                supplies,
                gateOffset,
                chestOffset,
                SpecialQuests.FARM,
                expectation
        );
    }

    /**
     * Fenced farm plot of bare tillable ground ({@code grass_block}, in {@code #questown:tillables})
     * with open sky above. The arborist plants saplings; {@code GrowTreesWarpRule} grows them into
     * real trees in the warp snapshot. Asserts the sapling supply is consumed (planting happened);
     * the grow→chop end-to-end is asserted by the full-cycle blueprint.
     */
    private static TestBlueprint arboristPlantSaplingBlueprint() {
        List<BlockPlacement> blocks = new ArrayList<>();

        int ox = 4;
        int oz = -3;

        for (int x = 0; x < 7; x++) {
            for (int z = 0; z < 7; z++) {
                boolean isEdge = x == 0 || x == 6 || z == 0 || z == 6;
                BlockPos offset = new BlockPos(ox + x, 0, oz + z);
                if (isEdge) {
                    if (x == 3 && z == 6) {
                        blocks.add(new BlockPlacement(offset, Blocks.OAK_FENCE_GATE.defaultBlockState()));
                    } else {
                        blocks.add(new BlockPlacement(offset, Blocks.OAK_FENCE.defaultBlockState()));
                    }
                } else {
                    // Tillable workspot with open air above for planting + tree growth.
                    blocks.add(new BlockPlacement(offset, Blocks.GRASS_BLOCK.defaultBlockState()));
                }
            }
        }

        BlockPos chestOffset = new BlockPos(ox + 1, 0, oz + 5);
        blocks.removeIf(bp -> bp.offset().equals(chestOffset));
        blocks.add(new BlockPlacement(chestOffset.below(), Blocks.DIRT.defaultBlockState()));

        BlockPos gateOffset = new BlockPos(ox + 3, 0, oz + 6);

        List<ItemStack> supplies = List.of(
                new ItemStack(Items.OAK_SAPLING, 16)
        );

        TestExpectation expectation = new TestExpectation(
                List.of(new ExpectedProduct("minecraft:oak_sapling", -16, -1)),
                1,
                100
        );

        return new TestBlueprint(
                RoomType.FARM,
                blocks,
                supplies,
                gateOffset,
                chestOffset,
                SpecialQuests.FARM,
                expectation
        );
    }

    /**
     * Arborist warp cycle: a fenced farm with a starter 4-high oak_log column at the floor (so
     * cut_trees is finishable from warp start) PLUS oak_sapling BLOCKS pre-seeded at the floor
     * (yCoord), as if planted in a prior warp. During the warp, {@code seedFarmSaplings} records
     * the saplings and {@code GrowTreesWarpRule} attempts to grow them via {@code growTreeAt}
     * (the in-warp worldgen path), while the cut_trees villager chops the starter column.
     * <p>
     * Asserts the full grow→chop cycle: >=5 logs, which is only reachable if the seeded sapling
     * grows into a tree (the starter column alone is 4) and cut_trees chops it in the same warp.
     * For growth to place a tree, the sapling needs a clear footprint — guaranteed by the jobs-track
     * flatten now clearing the whole build volume + tree headroom (see {@code TestArenaPreparer
     * .buildVolume}). See ADR-0005.
     */
    private static TestBlueprint arboristFullCycleBlueprint() {
        List<BlockPlacement> blocks = new ArrayList<>();

        int ox = 4;
        int oz = -3;

        for (int x = 0; x < 7; x++) {
            for (int z = 0; z < 7; z++) {
                boolean isEdge = x == 0 || x == 6 || z == 0 || z == 6;
                BlockPos offset = new BlockPos(ox + x, 0, oz + z);
                if (isEdge) {
                    if (x == 3 && z == 6) {
                        blocks.add(new BlockPlacement(offset, Blocks.OAK_FENCE_GATE.defaultBlockState()));
                    } else {
                        blocks.add(new BlockPlacement(offset, Blocks.OAK_FENCE.defaultBlockState()));
                    }
                } else {
                    // Dirt support a level below the floor; floor (yCoord) left open for saplings/trees.
                    blocks.add(new BlockPlacement(offset.below(), Blocks.DIRT.defaultBlockState()));
                }
            }
        }

        // Starter 4-high oak_log column (base at yCoord) so cut_trees is finishable at warp start.
        BlockPos trunkBase = new BlockPos(ox + 1, 0, oz + 1);
        for (int y = 0; y < 4; y++) {
            blocks.add(new BlockPlacement(trunkBase.above(y), Blocks.OAK_LOG.defaultBlockState()));
        }

        // One oak sapling centered in the 5x5 interior. An oak's foliage radius (2) spans the whole
        // interior, so only ONE tree fits — saplings any closer block each other's footprint (a
        // sapling block isn't "free" for growth). The starter column shares the plot because logs
        // ARE "free". seedFarmSaplings records this sapling and GrowTreesWarpRule grows it mid-warp
        // into a trunk at the floor (yCoord, where cut_trees scans), so it is chopped in the same warp.
        BlockPos saplingOffset = new BlockPos(ox + 3, 0, oz + 3);
        blocks.add(new BlockPlacement(saplingOffset, Blocks.OAK_SAPLING.defaultBlockState()));

        BlockPos chestOffset = new BlockPos(ox + 1, 0, oz + 5);
        blocks.add(new BlockPlacement(chestOffset.below(), Blocks.DIRT.defaultBlockState()));

        BlockPos gateOffset = new BlockPos(ox + 3, 0, oz + 6);

        List<ItemStack> supplies = List.of(
                new ItemStack(Items.WOODEN_AXE, 1)
        );

        TestExpectation expectation = new TestExpectation(
                List.of(
                        // The seeded sapling must grow and be chopped in the same warp: the warp
                        // fells one whole trunk here, and the starter column alone is only 4 logs, so
                        // >=5 can only be reached by chopping the GROWN oak. Growth is deterministic
                        // (TreeFeatureResolver.seededFor), so the grown trunk is a fixed 5 logs every
                        // run — robustly >=5, never the flaky 4..6 of level.random. See ADR-0005.
                        //
                        // LOAD-BEARING COORDINATE: the "5" is the deterministic oak height for the
                        // seed pos.asLong() of the sapling at (ox+3, oz+3) under the ORIGIN at the
                        // time of writing. Moving the arena origin or this sapling offset re-seeds the
                        // RNG and can drop the grown trunk to 4 (then this fails, predictably — not a
                        // flake). If you relocate it, re-pin this threshold to the new deterministic
                        // height. It also assumes the single felled trunk is the grown oak (job-block
                        // scan reaches (7,0) before the 4-log starter at (5,-2)).
                        new ExpectedProduct("minecraft:oak_log", 5, null)
                ),
                1,
                500
        );

        return new TestBlueprint(
                RoomType.FARM,
                blocks,
                supplies,
                gateOffset,
                chestOffset,
                SpecialQuests.FARM,
                expectation
        );
    }

    // --- Organizer / fetch (un-archived; see docs/plans/2026-06-08-001 and ADR-0005 precedent) ---

    // A distinctive ingredient guaranteed absent from the flattened arena. The fetch relocates it
    // from the supply store room to the requested target store room; a relocation is a town-wide
    // no-op, so the per-position container oracle (U1) is the only thing that can gate it.
    private static final String ORGANIZER_ITEM = "minecraft:emerald";
    private static final int ORGANIZER_FETCH_COUNT = 4;

    // Two store rooms either side of the flag (all offsets flag-relative). buildSupplyRoom(ox, oz)
    // builds only the SHELL (walls + door at (ox+2,0,oz+4)); chests are placed explicitly below.
    //
    // The TARGET chest (in the target room, placed by RoomBuilder.placeChest) holds the stock_request
    // and is its own delivery destination: TownContainers.setWorkSpot stamps the request's job-block
    // to the chest it sits in, and FetcherHack delivers the fetched item there. The requested
    // ingredient lives in a SEPARATE chest in the supply room — separate because
    // FetcherHack.containsUsableRequest only treats a request as "usable" if the requested item
    // exists in some chest OTHER than the request's own.
    private static final BlockPos ORGANIZER_TARGET_CHEST = new BlockPos(-4, 0, -1);
    private static final BlockPos ORGANIZER_TARGET_DOOR = new BlockPos(-4, 0, 2);
    private static final BlockPos ORGANIZER_SUPPLY_DOOR = new BlockPos(4, 0, 2);
    private static final BlockPos ORGANIZER_INGREDIENT_CHEST = new BlockPos(4, 0, -1);

    private static List<BlockPlacement> organizerRoomBlocks() {
        List<BlockPlacement> blocks = new ArrayList<>();
        blocks.addAll(buildSupplyRoom(-6, -2).blocks); // target store room shell (chest via placeChest)
        blocks.addAll(buildSupplyRoom(2, -2).blocks);  // supply store room shell
        blocks.add(new BlockPlacement(ORGANIZER_INGREDIENT_CHEST, Blocks.CHEST.defaultBlockState()));
        return blocks;
    }

    /**
     * Both-sides conservation: the target chest gains the ingredient AND the supply (ingredient)
     * chest loses it, while the town-wide total is unchanged (a relocation neither creates nor
     * destroys). Together this gates a real fetch and rejects the dupe (target gains, source
     * unchanged => town-wide +N) and loss (source loses, target unchanged => town-wide -N) modes.
     */
    private static TestExpectation organizerConservation() {
        return new TestExpectation(
                List.of(new ExpectedProduct(ORGANIZER_ITEM, 0, 0)),
                0, 0
        ).withContainerContents(List.of(
                new ExpectedContainerContent(ORGANIZER_TARGET_CHEST, ORGANIZER_ITEM, 1, null),
                new ExpectedContainerContent(ORGANIZER_INGREDIENT_CHEST, ORGANIZER_ITEM, null, -1)
        ));
    }

    /**
     * Post-placement setup: assert the distinctive ingredient is absent (setup-sanity guard against
     * arena residue), then fabricate the StockRequestItem in the target chest (mirroring
     * {@code CreateStockRequestFromUIMessage}: only the {@code request} NBT — the workspot is stamped
     * at runtime by {@code TownContainers.setWorkSpot}) and seed the ingredient in the supply chest.
     */
    private static TestBlueprint.PostPlacementSetup organizerSetupHook() {
        return (level, flagPos, town, output) -> {
            MCTownState state = town.captureCurrentState();
            if (state != null) {
                int pre = TestResultChecker.snapshotItemCounts(state).getOrDefault(ORGANIZER_ITEM, 0);
                if (pre != 0) {
                    output.error("[organizer] zero-pre-existing guard tripped: found " + pre
                            + " " + ORGANIZER_ITEM + " in town before seeding");
                    return false;
                }
            }
            BlockPos targetChest = flagPos.offset(ORGANIZER_TARGET_CHEST);
            BlockPos ingredientChest = flagPos.offset(ORGANIZER_INGREDIENT_CHEST);

            ItemStack request = ItemsInit.STOCK_REQUEST.get().getDefaultInstance();
            StockRequestItem.writeToNBT(request.getOrCreateTag(), WorkRequest.of(Items.EMERALD));
            if (!putInChest(level, targetChest, request)) {
                output.error("[organizer] target chest not found at " + targetChest.toShortString());
                return false;
            }
            if (!putInChest(level, ingredientChest, new ItemStack(Items.EMERALD, ORGANIZER_FETCH_COUNT))) {
                output.error("[organizer] ingredient chest not found at " + ingredientChest.toShortString());
                return false;
            }
            output.msg("[organizer] seeded request at " + targetChest.toShortString() + ", "
                    + ORGANIZER_FETCH_COUNT + " " + ORGANIZER_ITEM + " at " + ingredientChest.toShortString());
            return true;
        };
    }

    private static boolean putInChest(ServerLevel level, BlockPos pos, ItemStack stack) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof Container container)) {
            return false;
        }
        container.setItem(0, stack);
        return true;
    }

    /**
     * Phase A — realtime green baseline. Proves the realtime fetch relocates the requested
     * ingredient source->target (request chest gains it, ingredient chest loses it). Warp is
     * skipped (the warp model is the deferred follow-up track — see the Phase B XFAIL below).
     * <p>
     * Greening this required repairing the fetcher's realtime status seam, which had rotted while
     * the job sat archived: {@code FetcherHack.computeStatusOverride} (wired via {@code
     * DeclarativeJob.getComputeStatusOverrideForSpecialJobs}) now drives the acquire→fetch→deliver
     * status, that override is propagated into {@code journal.getStatus()} (so the supply getter
     * actually engages), and {@code TownContainers.setWorkSpot} guards a null warp-scan room.
     */
    private static TestBlueprint organizerFetchBlueprint() {
        TestBlueprint base = new TestBlueprint(
                RoomType.INDOOR,
                organizerRoomBlocks(),
                List.of(), // the setup hook seeds chests; no auto-filled supplies
                ORGANIZER_TARGET_DOOR,
                ORGANIZER_TARGET_CHEST,
                SpecialQuests.STORE_ROOM_SMALL,
                organizerConservation(),
                ORGANIZER_SUPPLY_DOOR, // supplyDoorOffset: registers the supply room
                null,                  // warpAmountOverride
                null,                  // startTimeTick
                null,                  // villagerCount
                true,                  // realtimePhase
                1500,                  // realtimeTicks: multi-hop fetch budget, verified ample (the
                                       // fetcher loops continuously, relocating >=1 emerald well within)
                false,                 // drainHungerBeforeTest
                true,                  // skipWarp
                organizerConservation(), // realtimeExpectation: the per-position conservation gate
                null,                  // minExpectedFullnessAfter
                null,                  // extraBlockRoomOffset
                null,                  // extraBlockRoomId
                null,                  // expectedVillagerHeld
                false                  // useNaturalWarp
        );
        return base.withSetupHook(organizerSetupHook());
    }

    /**
     * Phase B — warp fetch relocation, green. Same setup as Phase A but runs the warp path, which
     * now has a fetch model: {@code RelocateRequestedItemWarpRule} (declared as a global rule in
     * organizer_fetcher.json) moves the requested ingredient source->target on the warp town state,
     * so the per-position conservation holds. This supersedes the former XFAIL spec; the warp gap is
     * closed.
     */
    private static TestBlueprint organizerFetchWarpBlueprint() {
        TestBlueprint base = new TestBlueprint(
                RoomType.INDOOR,
                organizerRoomBlocks(),
                List.of(),
                ORGANIZER_TARGET_DOOR,
                ORGANIZER_TARGET_CHEST,
                SpecialQuests.STORE_ROOM_SMALL,
                organizerConservation(), // warp checkResults gates on this (incl. containerContents)
                ORGANIZER_SUPPLY_DOOR,
                null,                  // warpAmountOverride (suite default)
                null,                  // startTimeTick
                null,                  // villagerCount
                false,                 // realtimePhase = false (warp-only)
                null,                  // realtimeTicks
                false,                 // drainHungerBeforeTest
                false,                 // skipWarp = false (run warp)
                null,                  // realtimeExpectation
                null, null, null, null,
                false                  // useNaturalWarp
        );
        return base.withSetupHook(organizerSetupHook());
    }

    private static TestBlueprint cookBlueprint() {
        List<BlockPlacement> blocks = new ArrayList<>();

        int ox = 4;
        int oz = -2;

        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                boolean isEdge = x == 0 || x == 4 || z == 0 || z == 4;
                BlockPos floorOffset = new BlockPos(ox + x, -1, oz + z);
                blocks.add(new BlockPlacement(floorOffset, Blocks.COBBLESTONE.defaultBlockState()));

                if (isEdge) {
                    blocks.add(new BlockPlacement(new BlockPos(ox + x, 0, oz + z), Blocks.COBBLESTONE.defaultBlockState()));
                    blocks.add(new BlockPlacement(new BlockPos(ox + x, 1, oz + z), Blocks.COBBLESTONE.defaultBlockState()));
                }

                BlockPos ceilOffset = new BlockPos(ox + x, 2, oz + z);
                blocks.add(new BlockPlacement(ceilOffset, Blocks.COBBLESTONE.defaultBlockState()));
            }
        }

        BlockPos doorLower = new BlockPos(ox + 2, 0, oz + 4);
        BlockPos doorUpper = new BlockPos(ox + 2, 1, oz + 4);
        blocks.removeIf(bp -> bp.offset().equals(doorLower) || bp.offset().equals(doorUpper));
        blocks.add(new BlockPlacement(doorLower, Blocks.OAK_DOOR.defaultBlockState()));
        blocks.add(new BlockPlacement(doorUpper, Blocks.OAK_DOOR.defaultBlockState().setValue(
                net.minecraft.world.level.block.DoorBlock.HALF,
                net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER
        )));

        BlockPos furnaceOffset = new BlockPos(ox + 1, 0, oz + 1);
        blocks.add(new BlockPlacement(furnaceOffset, Blocks.FURNACE.defaultBlockState()));

        BlockPos chestOffset = new BlockPos(ox + 3, 0, oz + 1);

        List<ItemStack> supplies = List.of(
                new ItemStack(Items.BEEF, 32),
                new ItemStack(Items.COAL, 32),
                new ItemStack(Items.STICK, 16)
        );

        TestExpectation expectation = new TestExpectation(
                List.of(
                        new ExpectedProduct("minecraft:cooked_beef", 3, 8),
                        new ExpectedProduct("minecraft:beef", -8, -3),
                        new ExpectedProduct("minecraft:coal", -5, -2)
                ),
                1,
                100
        );

        return new TestBlueprint(
                RoomType.INDOOR,
                blocks,
                supplies,
                doorLower,
                chestOffset,
                new ResourceLocation(Questown.MODID, "kitchen_small"),
                expectation
        );
    }

    private static TestBlueprint bakerBlueprint() {
        return indoorRoomBlueprint(
                BlocksInit.BREAD_OVEN_BLOCK.get().defaultBlockState(),
                null,
                List.of(
                        new ItemStack(Items.WHEAT, 32),
                        new ItemStack(Items.COAL, 16)
                ),
                new ResourceLocation(Questown.MODID, "breadmaker"),
                new TestExpectation(
                        List.of(new ExpectedProduct("minecraft:bread", 1, null)),
                        1, 100
                )
        );
    }

    private static boolean isArmorerJob(JobID jobId) {
        String j = jobId.jobId();
        return j.endsWith("_boots") || j.endsWith("_helmet")
                || j.endsWith("_leggings") || j.endsWith("_chestplate");
    }

    private static TestBlueprint armorerBlueprint() {
        List<BlockPlacement> blocks = new ArrayList<>();

        int ox = 4;
        int oz = -2;

        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                boolean isEdge = x == 0 || x == 4 || z == 0 || z == 4;
                BlockPos floorOffset = new BlockPos(ox + x, -1, oz + z);
                blocks.add(new BlockPlacement(floorOffset, Blocks.COBBLESTONE.defaultBlockState()));

                if (isEdge) {
                    blocks.add(new BlockPlacement(new BlockPos(ox + x, 0, oz + z), Blocks.COBBLESTONE.defaultBlockState()));
                    blocks.add(new BlockPlacement(new BlockPos(ox + x, 1, oz + z), Blocks.COBBLESTONE.defaultBlockState()));
                }

                BlockPos ceilOffset = new BlockPos(ox + x, 2, oz + z);
                blocks.add(new BlockPlacement(ceilOffset, Blocks.COBBLESTONE.defaultBlockState()));
            }
        }

        BlockPos doorLower = new BlockPos(ox + 2, 0, oz + 4);
        BlockPos doorUpper = new BlockPos(ox + 2, 1, oz + 4);
        blocks.removeIf(bp -> bp.offset().equals(doorLower) || bp.offset().equals(doorUpper));
        blocks.add(new BlockPlacement(doorLower, Blocks.OAK_DOOR.defaultBlockState()));
        blocks.add(new BlockPlacement(doorUpper, Blocks.OAK_DOOR.defaultBlockState().setValue(
                net.minecraft.world.level.block.DoorBlock.HALF,
                net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER
        )));

        blocks.add(new BlockPlacement(new BlockPos(ox + 1, 0, oz + 1), BlocksInit.BLACKSMITHS_TABLE_BLOCK.get().defaultBlockState()));
        blocks.add(new BlockPlacement(new BlockPos(ox + 3, 0, oz + 1), Blocks.CHEST.defaultBlockState()));
        blocks.add(new BlockPlacement(new BlockPos(ox + 1, 0, oz + 3), Blocks.CHEST.defaultBlockState()));
        blocks.add(new BlockPlacement(new BlockPos(ox + 3, 0, oz + 3), Blocks.CHEST.defaultBlockState()));
        blocks.add(new BlockPlacement(new BlockPos(ox + 2, 0, oz + 1), Blocks.CHEST.defaultBlockState()));

        BlockPos chestOffset = new BlockPos(ox + 1, 0, oz + 3);

        List<ItemStack> supplies = List.of(
                new ItemStack(Items.LEATHER, 32)
        );

        TestExpectation expectation = new TestExpectation(
                List.of(new ExpectedProduct("minecraft:leather", -32, null)),
                0, 0
        );

        return new TestBlueprint(
                RoomType.INDOOR,
                blocks,
                supplies,
                doorLower,
                chestOffset,
                new ResourceLocation(Questown.MODID, "armory"),
                expectation
        );
    }

    private static TestBlueprint crafterBlueprint() {
        return indoorRoomBlueprint(
                Blocks.CRAFTING_TABLE.defaultBlockState(),
                null,
                List.of(new ItemStack(Items.OAK_SAPLING, 16)),
                new ResourceLocation(Questown.MODID, "crafting_room"),
                new TestExpectation(
                        List.of(new ExpectedProduct("minecraft:stick", 1, null)),
                        1, 100
                )
        );
    }

    private static TestBlueprint blacksmithBlueprint() {
        return indoorRoomBlueprint(
                BlocksInit.BLACKSMITHS_TABLE_BLOCK.get().defaultBlockState(),
                Blocks.TORCH.defaultBlockState(),
                List.of(
                        new ItemStack(Items.STICK, 16),
                        new ItemStack(Items.OAK_PLANKS, 32)
                ),
                new ResourceLocation(Questown.MODID, "smithy"),
                new TestExpectation(
                        List.of(
                                new ExpectedProduct("minecraft:wooden_axe", 0, null),
                                new ExpectedProduct("minecraft:wooden_pickaxe", 0, null),
                                new ExpectedProduct("minecraft:wooden_hoe", 0, null),
                                new ExpectedProduct("minecraft:wooden_shovel", 0, null)
                        ),
                        1, 100
                )
        );
    }

    private static TestBlueprint smelterBlueprint() {
        return indoorRoomBlueprint(
                BlocksInit.ORE_PROCESSING_BLOCK.get().defaultBlockState(),
                null,
                List.of(
                        new ItemStack(Items.IRON_ORE, 16),
                        new ItemStack(Items.STONE_PICKAXE, 1)
                ),
                new ResourceLocation(Questown.MODID, "smeltery"),
                new TestExpectation(
                        List.of(new ExpectedProduct("minecraft:raw_iron", 1, null)),
                        1, 100
                )
        );
    }

    private static TestBlueprint soupCookBlueprint() {
        return indoorRoomBlueprint(
                BlocksInit.SOUP_POT_SMALL.get().defaultBlockState(),
                null,
                List.of(
                        new ItemStack(Items.RED_MUSHROOM, 16),
                        new ItemStack(Items.BOWL, 16),
                        new ItemStack(Items.WOODEN_SHOVEL, 1)
                ),
                new ResourceLocation(Questown.MODID, "soup_kitchen_small"),
                new TestExpectation(
                        List.of(new ExpectedProduct("minecraft:mushroom_stew", 1, null)),
                        1, 100
                )
        );
    }

    private static TestBlueprint indoorRoomBlueprint(
            net.minecraft.world.level.block.state.BlockState workBlock,
            @Nullable net.minecraft.world.level.block.state.BlockState extraBlock,
            List<ItemStack> supplies,
            ResourceLocation roomId,
            TestExpectation expectation
    ) {
        List<BlockPlacement> blocks = new ArrayList<>();

        int ox = 4;
        int oz = -2;

        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                boolean isEdge = x == 0 || x == 4 || z == 0 || z == 4;
                BlockPos floorOffset = new BlockPos(ox + x, -1, oz + z);
                blocks.add(new BlockPlacement(floorOffset, Blocks.COBBLESTONE.defaultBlockState()));

                if (isEdge) {
                    blocks.add(new BlockPlacement(new BlockPos(ox + x, 0, oz + z), Blocks.COBBLESTONE.defaultBlockState()));
                    blocks.add(new BlockPlacement(new BlockPos(ox + x, 1, oz + z), Blocks.COBBLESTONE.defaultBlockState()));
                }

                BlockPos ceilOffset = new BlockPos(ox + x, 2, oz + z);
                blocks.add(new BlockPlacement(ceilOffset, Blocks.COBBLESTONE.defaultBlockState()));
            }
        }

        BlockPos doorLower = new BlockPos(ox + 2, 0, oz + 4);
        BlockPos doorUpper = new BlockPos(ox + 2, 1, oz + 4);
        blocks.removeIf(bp -> bp.offset().equals(doorLower) || bp.offset().equals(doorUpper));
        blocks.add(new BlockPlacement(doorLower, Blocks.OAK_DOOR.defaultBlockState()));
        blocks.add(new BlockPlacement(doorUpper, Blocks.OAK_DOOR.defaultBlockState().setValue(
                net.minecraft.world.level.block.DoorBlock.HALF,
                net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER
        )));

        blocks.add(new BlockPlacement(new BlockPos(ox + 1, 0, oz + 1), workBlock));

        if (extraBlock != null) {
            blocks.add(new BlockPlacement(new BlockPos(ox + 3, 0, oz + 1), extraBlock));
        }

        BlockPos chestOffset = new BlockPos(ox + 3, 0, oz + 3);

        return new TestBlueprint(
                RoomType.INDOOR,
                blocks,
                supplies,
                doorLower,
                chestOffset,
                roomId,
                expectation
        );
    }

    private static TestBlueprint gathererBlueprint() {
        return welcomeMatBlueprint(
                List.of(
                        new ItemStack(Items.STONE_AXE, 1),
                        new ItemStack(Items.COOKED_BEEF, 8)
                )
        );
    }

    private static TestBlueprint explorerBlueprint() {
        // The ==1 knowledge-growth assertion is bounded by SUPPLY, not by warp ticks:
        // 1 paper => exactly 1 gatherer map can be made => exactly 1 scout => knowledge grows by 1.
        // The cooked_beef just keeps the villager fed. Load-bearing — do not raise the paper count.
        // The warp amount only needs to be "big enough" to complete one full leaver cycle; 2500 was
        // too short (the villager produced nothing), so we match the suite default that every other
        // passing leaver job (gatherer:axe, hunter, miner, fisher) uses.
        // The explorer is the only leaver job whose product REPLACES a consumed input:
        // it turns 1 paper + 1 food into 1 gatherer map, so the town's net item count is
        // unchanged. The inherited wildcard ("* grows by >= 1") therefore can't gate it —
        // assert the actual product (exactly one gatherer map) plus the knowledge growth.
        return welcomeMatBlueprint(List.of(
                new ItemStack(Items.PAPER, 1),
                new ItemStack(Items.COOKED_BEEF, 1)
        )).withExpectation(new TestExpectation(
                List.of(
                        new ExpectedProduct("questown:gatherer_map", 1, 1),
                        // Paper is carried as a held tool but consumed once per trip
                        // (CONSUME_HELD_PAPER) so it isn't infinitely reusable.
                        new ExpectedProduct("minecraft:paper", -1, -1)
                ),
                1, 100
        )).withMinKnowledgeGrowth(1).withWarpAmountOverride(24000);
    }

    private static TestBlueprint hunterBlueprint() {
        return welcomeMatBlueprint(
                List.of(
                        new ItemStack(Items.STONE_SWORD, 1),
                        new ItemStack(Items.COOKED_BEEF, 8)
                )
        );
    }

    private static TestBlueprint welcomeMatBlueprint(List<ItemStack> supplies) {
        List<BlockPlacement> blocks = new ArrayList<>();

        BlockPos matOffset = new BlockPos(3, 0, 0);
        blocks.add(new BlockPlacement(matOffset, BlocksInit.WELCOME_MAT_BLOCK.get().defaultBlockState()));

        SupplyRoom sr = buildSupplyRoom(4, -2);
        blocks.addAll(sr.blocks);

        return new TestBlueprint(
                RoomType.WELCOME_MAT,
                blocks,
                supplies,
                matOffset,
                sr.chestOffset,
                SpecialQuests.TOWN_GATE,
                wildcardExpectation(),
                sr.doorOffset
        );
    }

    private static TestBlueprint minerBlueprint() {
        List<BlockPlacement> blocks = new ArrayList<>();

        BlockPos blockOffset = new BlockPos(3, 0, 0);
        blocks.add(new BlockPlacement(blockOffset, BlocksInit.MINESHAFT.get().defaultBlockState()));

        SupplyRoom sr = buildSupplyRoom(4, -2);
        blocks.addAll(sr.blocks);

        return new TestBlueprint(
                RoomType.BLOCK_ROOM,
                blocks,
                List.of(
                        new ItemStack(Items.STONE_PICKAXE, 1),
                        new ItemStack(Items.COOKED_BEEF, 8)
                ),
                blockOffset,
                sr.chestOffset,
                Questown.ResourceLocation("block_room/block.questown.mineshaft"),
                wildcardExpectation(),
                sr.doorOffset
        );
    }

    private static TestBlueprint fisherBlueprint() {
        List<BlockPlacement> blocks = new ArrayList<>();

        BlockPos blockOffset = new BlockPos(3, 0, 0);
        blocks.add(new BlockPlacement(blockOffset, BlocksInit.FISHING_STATION_BLOCK.get().defaultBlockState()));

        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                BlockPos waterPos = new BlockPos(3 + x, -1, -2 + z);
                blocks.add(new BlockPlacement(waterPos, Blocks.WATER.defaultBlockState()));
            }
        }

        SupplyRoom sr = buildSupplyRoom(4, -2);
        blocks.addAll(sr.blocks);

        return new TestBlueprint(
                RoomType.BLOCK_ROOM,
                blocks,
                List.of(new ItemStack(Items.STRING, 16)),
                blockOffset,
                sr.chestOffset,
                Questown.ResourceLocation("block_room/block.questown.fishing_station"),
                wildcardExpectation(),
                sr.doorOffset
        );
    }

    private record SupplyRoom(
            List<BlockPlacement> blocks,
            BlockPos chestOffset,
            BlockPos doorOffset
    ) {}

    private static SupplyRoom buildSupplyRoom(int ox, int oz) {
        List<BlockPlacement> blocks = new ArrayList<>();

        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                boolean isEdge = x == 0 || x == 4 || z == 0 || z == 4;
                blocks.add(new BlockPlacement(
                        new BlockPos(ox + x, -1, oz + z),
                        Blocks.COBBLESTONE.defaultBlockState()
                ));
                if (isEdge) {
                    blocks.add(new BlockPlacement(
                            new BlockPos(ox + x, 0, oz + z),
                            Blocks.COBBLESTONE.defaultBlockState()
                    ));
                    blocks.add(new BlockPlacement(
                            new BlockPos(ox + x, 1, oz + z),
                            Blocks.COBBLESTONE.defaultBlockState()
                    ));
                }
                blocks.add(new BlockPlacement(
                        new BlockPos(ox + x, 2, oz + z),
                        Blocks.COBBLESTONE.defaultBlockState()
                ));
            }
        }

        BlockPos doorLower = new BlockPos(ox + 2, 0, oz + 4);
        BlockPos doorUpper = new BlockPos(ox + 2, 1, oz + 4);
        blocks.removeIf(bp -> bp.offset().equals(doorLower) || bp.offset().equals(doorUpper));
        blocks.add(new BlockPlacement(doorLower, Blocks.OAK_DOOR.defaultBlockState()));
        blocks.add(new BlockPlacement(doorUpper, Blocks.OAK_DOOR.defaultBlockState().setValue(
                net.minecraft.world.level.block.DoorBlock.HALF,
                net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER
        )));

        BlockPos chestOffset = new BlockPos(ox + 2, 0, oz + 1);

        return new SupplyRoom(blocks, chestOffset, doorLower);
    }

    // --- Perf scenarios ---
    //
    // These are MEASUREMENTS, not gates: they always pass and report flag-tick timings, so a
    // regression shows up as a number in the log rather than a red scenario. The point is the
    // scaling shape (tick cost vs townie count vs registered-room count), which is why they come
    // in a small/large pair — one run gives you a baseline and a loaded town to compare it to.
    //
    // The expectation is deliberately empty (no products, 0 cycles): TestResultChecker's product
    // loop and cycle gate are both skipped, so the scenario cannot fail on production outcomes.

    private static TestBlueprint perfBlueprint(int villagers, int extraRooms, int ticks) {
        SupplyRoom base = buildSupplyRoom(-6, -2);
        TestBlueprint bp = new TestBlueprint(
                RoomType.INDOOR,
                base.blocks(),
                List.of(),
                base.doorOffset(),
                base.chestOffset(),
                SpecialQuests.STORE_ROOM_SMALL,
                new TestExpectation(List.of(), 0, 0),
                null,      // supplyDoorOffset
                null,      // warpAmountOverride
                null,      // startTimeTick
                villagers,
                true,      // realtimePhase: the whole point is realtime tick cost
                ticks,
                false,     // drainHungerBeforeTest
                true,      // skipWarp
                new TestExpectation(List.of(), 0, 0),
                null, null, null, null,
                false      // useNaturalWarp
        );
        return bp.withSetupHook(perfExtraRoomsSetup(extraRooms))
                 .withPostSpawnAction(perfStartProfiling())
                 .withCustomAssertion(perfReport(villagers, extraRooms));
    }

    /**
     * Builds {@code count} additional enclosed 5x5 shells in a ring around the flag and registers
     * each door, to load the room scan and the container scan. Each shell is self-enclosing
     * (floor, walls, ceiling), so surrounding terrain cannot break the enclosure — and the report
     * prints the room count the town actually ended up with, so a shell that failed to resolve
     * shows up in the measurement rather than silently inflating it.
     */
    private static TestBlueprint.PostPlacementSetup perfExtraRoomsSetup(int count) {
        return (level, flagPos, town, output) -> {
            int placed = 0;
            for (int i = 0; i < count; i++) {
                // Ring the flag at a radius that clears the base room at (-6,-2).
                int ring = 1 + (i / 8);
                double angle = (i % 8) * (Math.PI / 4);
                int ox = (int) Math.round(Math.cos(angle) * 9 * ring) - 2;
                int oz = (int) Math.round(Math.sin(angle) * 9 * ring) - 2;

                SupplyRoom room = buildSupplyRoom(ox, oz);
                for (BlockPlacement bp : room.blocks()) {
                    level.setBlockAndUpdate(flagPos.offset(bp.offset()), bp.blockState());
                }
                // A bare shell matches no room RECIPE, and the container scan iterates recipe
                // matches — so a chest is what makes the room count toward the cost being measured.
                level.setBlockAndUpdate(
                        flagPos.offset(room.chestOffset()),
                        Blocks.CHEST.defaultBlockState()
                );
                town.getRoomHandle().registerDoor(flagPos.offset(room.doorOffset()));
                placed++;
            }
            output.msg("[perf] built and registered " + placed + " extra room shell(s)");
            return true;
        };
    }

    private static TestBlueprint.PostSpawnAction perfStartProfiling() {
        return (level, flagPos, town, output) -> {
            ca.bradj.questown.town.TickProfile.INSTANCE.enable();
            output.msg("[perf] profiling enabled; measuring flag-tick cost from here");
            return true;
        };
    }

    private static TestBlueprint.CustomAssertion perfReport(int villagers, int extraRooms) {
        return (level, flagPos, town, output) -> {
            ca.bradj.questown.town.TickProfile.Snapshot snap =
                    ca.bradj.questown.town.TickProfile.INSTANCE.snapshot();
            long actualVillagers = town.getVillagerHandle().size();
            int actualRooms = town.getRoomHandle().getMatches(x -> true).size();

            output.msg(String.format(
                    "[perf] townies=%d (requested %d) rooms=%d (requested %d extra)",
                    actualVillagers, villagers, actualRooms, extraRooms
            ));
            output.msg("[perf] flag tick: " + snap.describe());
            for (String line : ca.bradj.questown.town.TickProfile.INSTANCE.describePhases()) {
                output.msg("[perf]" + line);
            }

            ca.bradj.questown.town.TickProfile.INSTANCE.disable();
            return true;
        };
    }

    private static TestEntry perfEntry(String name, TestBlueprint bp) {
        return new TestEntry("perf/" + name, new JobID("farmer", "harvest_wheat"), bp, "perf");
    }

    private static TestExpectation wildcardExpectation() {
        return new TestExpectation(
                List.of(new ExpectedProduct("*", 1, null)),
                1, 100
        );
    }

    // --- Edge case blueprints ---

    private static TestBlueprint farmerNightStartBlueprint() {
        TestBlueprint base = farmerBlueprint();
        return new TestBlueprint(
                base.roomType(), base.blocks(), base.supplyItems(),
                base.doorOrGateOffset(), base.chestOffset(), base.roomId(),
                new TestExpectation(
                        List.of(new ExpectedProduct("minecraft:wheat", 1, null)),
                        1, 100
                ),
                base.supplyDoorOffset(), 12000, 20000L, null, false, null,
                false, false, null, null, null, null, null, false
        );
    }

    private static TestBlueprint farmerAllNightBlueprint() {
        TestBlueprint base = farmerBlueprint();
        // No hoe in supplies: prevents real-time harvesting during settle phases,
        // so the only way wheat could appear is if the warp ran (which it shouldn't at night).
        return new TestBlueprint(
                base.roomType(), base.blocks(), List.of(),
                base.doorOrGateOffset(), base.chestOffset(), base.roomId(),
                new TestExpectation(
                        List.of(new ExpectedProduct("minecraft:wheat", 0, 0)),
                        0, 0
                ),
                base.supplyDoorOffset(), 2000, 15000L, null, false, null,
                false, false, null, null, null, null, null, false
        );
    }

    private static TestBlueprint farmerMultiDayBlueprint() {
        TestBlueprint base = farmerBlueprint();
        return new TestBlueprint(
                base.roomType(), base.blocks(), base.supplyItems(),
                base.doorOrGateOffset(), base.chestOffset(), base.roomId(),
                new TestExpectation(
                        List.of(new ExpectedProduct("minecraft:wheat", 3, null)),
                        3, 300
                ),
                base.supplyDoorOffset(), 72000, 0L, null, false, null,
                false, false, null, null, null, null, null, false
        );
    }

    private static TestBlueprint farmerNoSuppliesBlueprint() {
        TestBlueprint base = farmerBlueprint();
        return new TestBlueprint(
                base.roomType(), base.blocks(), List.of(),
                base.doorOrGateOffset(), base.chestOffset(), base.roomId(),
                new TestExpectation(
                        List.of(new ExpectedProduct("minecraft:wheat", 0, 0)),
                        0, 0
                ),
                base.supplyDoorOffset(), 24000, 0L, null, false, null,
                false, false, null, null, null, null, null, false
        ).withCustomAssertion((level, flagPos, town, output) -> {
            // Legibility pass (ADR-0011): with no supplies, the farmer must be
            // flagged UNMET_ITEM so the need bubble can say so. Silence means working.
            List<TownieNeed> needs = town.getVillagerHandle().entities().stream()
                    .map(v -> ((VisitorMobEntity) v).getNeed())
                    .toList();
            output.msg("Townie needs at end of no-supplies run: " + needs);
            return !needs.isEmpty() && needs.stream().allMatch(n -> n == TownieNeed.UNMET_ITEM);
        });
    }

    private static TestBlueprint gathererToolDurabilityBlueprint() {
        TestBlueprint base = welcomeMatBlueprint(
                List.of(
                        new ItemStack(Items.WOODEN_AXE, 1),
                        new ItemStack(Items.COOKED_BEEF, 8)
                )
        );
        return new TestBlueprint(
                base.roomType(), base.blocks(), base.supplyItems(),
                base.doorOrGateOffset(), base.chestOffset(), base.roomId(),
                new TestExpectation(
                        List.of(
                                new ExpectedProduct("minecraft:wooden_axe", -1, 0),
                                new ExpectedProduct("*", 1, null)
                        ),
                        1, 300
                ),
                base.supplyDoorOffset(), 240000, 0L, null, false, null,
                false, false, null, null, null, null, null, false
        );
    }

    private static TestBlueprint farmerTwoVillagersBlueprint() {
        TestBlueprint base = farmerBlueprint();
        return new TestBlueprint(
                base.roomType(), base.blocks(), base.supplyItems(),
                base.doorOrGateOffset(), base.chestOffset(), base.roomId(),
                new TestExpectation(
                        List.of(new ExpectedProduct("minecraft:wheat", 2, null)),
                        1, 100
                ),
                base.supplyDoorOffset(), 24000, 0L, 2, false, null,
                false, false, null, null, null, null, null, false
        );
    }

    private static TestBlueprint farmerRealtimeBlueprint() {
        TestBlueprint base = farmerBlueprint();
        return new TestBlueprint(
                base.roomType(), base.blocks(), base.supplyItems(),
                base.doorOrGateOffset(), base.chestOffset(), base.roomId(),
                base.expectation(),
                base.supplyDoorOffset(), null, null, null, true, 4800,
                false, false, null, null, null, null, null, false
        );
    }

    private static TestBlueprint gathererShortAbsenceBlueprint() {
        TestBlueprint base = welcomeMatBlueprint(
                List.of(
                        new ItemStack(Items.STONE_AXE, 1),
                        new ItemStack(Items.COOKED_BEEF, 8)
                )
        );
        return new TestBlueprint(
                base.roomType(), base.blocks(), base.supplyItems(),
                base.doorOrGateOffset(), base.chestOffset(), base.roomId(),
                new TestExpectation(
                        List.of(new ExpectedProduct("*", 0, 14)),
                        0, 100
                ),
                base.supplyDoorOffset(), 2000, 0L, null, false, null,
                false, false, null, null, null, null, null, true
        );
    }

    /**
     * Simulates the campfire sleep scenario: player is in town at evening,
     * sleeps through night, dayTime jumps ~11000 ticks to morning.
     * The flag was ticking normally before sleep, so its reference tick is
     * current. The dayTime jump should NOT cause a massive warp.
     * startTimeTick=12000 (evening), warpAmount=11000 (jump to next morning).
     */
    private static TestBlueprint gathererSleepJumpBlueprint() {
        TestBlueprint base = welcomeMatBlueprint(
                List.of(
                        new ItemStack(Items.STONE_AXE, 1),
                        new ItemStack(Items.COOKED_BEEF, 8)
                )
        );
        return new TestBlueprint(
                base.roomType(), base.blocks(), base.supplyItems(),
                base.doorOrGateOffset(), base.chestOffset(), base.roomId(),
                new TestExpectation(
                        List.of(new ExpectedProduct("*", 0, 14)),
                        0, 100
                ),
                base.supplyDoorOffset(), 11000, 12000L, null, false, null,
                false, false, null, null, null, null, null, true
        );
    }

    /**
     * Simulates: player was briefly away (tp'd back after dying), then slept.
     * Combined effect: short real absence + sleep time jump.
     * startTimeTick=10000 (afternoon), warpAmount=14000 (past next morning).
     */
    private static TestBlueprint gathererAbsenceThenSleepBlueprint() {
        TestBlueprint base = welcomeMatBlueprint(
                List.of(
                        new ItemStack(Items.STONE_AXE, 1),
                        new ItemStack(Items.COOKED_BEEF, 8)
                )
        );
        return new TestBlueprint(
                base.roomType(), base.blocks(), base.supplyItems(),
                base.doorOrGateOffset(), base.chestOffset(), base.roomId(),
                new TestExpectation(
                        List.of(new ExpectedProduct("*", 0, 14)),
                        0, 100
                ),
                base.supplyDoorOffset(), 14000, 10000L, null, false, null,
                false, false, null, null, null, null, null, true
        );
    }

    // --- Eating tests ---

    /**
     * Villager eats cooked food at the town flag (no dining room).
     * DinerNoTableWork path: hungry -> get food from supply -> eat at flag -> fullness 100%.
     * Fullness cycles between 0-100% during monitoring; threshold reflects end-of-window value.
     */
    private static TestBlueprint eatNoTableBlueprint() {
        TestBlueprint base = welcomeMatBlueprint(List.of(
                new ItemStack(Items.BREAD, 16)
        ));
        return new TestBlueprint(
                base.roomType(), base.blocks(), base.supplyItems(),
                base.doorOrGateOffset(), base.chestOffset(), base.roomId(),
                new TestExpectation(List.of(), 0, 0),
                base.supplyDoorOffset(), null, null, null, true, 800,
                true, true,
                new TestExpectation(
                        List.of(new ExpectedProduct("minecraft:bread", -16, null)),
                        1, 1
                ),
                // fullness check omitted: hunger cycles every ~150 ticks, final value at 800t is timing-dependent
                null, null, null,
                new TestExpectation(List.of(new ExpectedProduct("minecraft:bread", 0, 1)), 0, 1),
                false
        );
    }

    /**
     * Villager eats at a dining room (plate block present).
     * DinerWork path: hungry -> get food from supply -> eat at plate block -> fullness 100%.
     */
    private static TestBlueprint dineAtTimeBlueprint() {
        TestBlueprint base = welcomeMatBlueprint(List.of(
                new ItemStack(Items.BREAD, 16)
        ));
        BlockPos plateOffset = new BlockPos(1, 0, 0);
        List<BlockPlacement> blocks = new ArrayList<>(base.blocks());
        blocks.add(new BlockPlacement(plateOffset, BlocksInit.PLATE_BLOCK.get().defaultBlockState()));
        return new TestBlueprint(
                base.roomType(), blocks, base.supplyItems(),
                base.doorOrGateOffset(), base.chestOffset(), base.roomId(),
                new TestExpectation(List.of(), 0, 0),
                base.supplyDoorOffset(), null, null, null, true, 800,
                true, true,
                new TestExpectation(
                        List.of(new ExpectedProduct("minecraft:bread", -16, null)),
                        1, 1
                ),
                0.25f, plateOffset, SpecialQuests.DINING_ROOM,
                new TestExpectation(List.of(new ExpectedProduct("minecraft:bread", 0, 1)), 0, 1),
                false
        );
    }

    /**
     * Villager eats raw food at the town flag (no cooked food available).
     * DinerRawFoodWork path: hungry -> no cooked food -> eat raw food -> fullness ~50%.
     * Requires more realtime ticks because DinerNoTableWork must time out first.
     */
    private static TestBlueprint eatRawFoodBlueprint() {
        TestBlueprint base = welcomeMatBlueprint(List.of(
                new ItemStack(Items.BEEF, 16)
        ));
        return new TestBlueprint(
                base.roomType(), base.blocks(), base.supplyItems(),
                base.doorOrGateOffset(), base.chestOffset(), base.roomId(),
                new TestExpectation(List.of(), 0, 0),
                base.supplyDoorOffset(), null, null, null, true, 2000,
                true, true,
                new TestExpectation(
                        List.of(new ExpectedProduct("minecraft:beef", -16, null)),
                        1, 1
                ),
                0.3f, null, null,
                new TestExpectation(List.of(new ExpectedProduct("minecraft:beef", 0, 1)), 0, 1),
                false
        );
    }

    /**
     * Directly assigns dining_no_table job (bypassing the hunger trigger).
     * Tests that the eating job itself works: collects food from supply and restores fullness.
     */
    private static TestBlueprint eatDirectBlueprint() {
        TestBlueprint base = welcomeMatBlueprint(List.of(
                new ItemStack(Items.BREAD, 16)
        ));
        return new TestBlueprint(
                base.roomType(), base.blocks(), base.supplyItems(),
                base.doorOrGateOffset(), base.chestOffset(), base.roomId(),
                new TestExpectation(List.of(), 0, 0),
                base.supplyDoorOffset(), null, null, null, true, 600,
                false, true,
                new TestExpectation(
                        List.of(new ExpectedProduct("minecraft:bread", -16, null)),
                        1, 1
                ),
                0.25f, null, null,
                new TestExpectation(List.of(new ExpectedProduct("minecraft:bread", 0, 1)), 0, 1),
                false
        );
    }

    // --- Worldgen checks ---

    private static LevelCheck emptyTownStructureCheck() {
        return new LevelCheck(
                "worldgen/empty_town_structure",
                "worldgen",
                level -> level.registryAccess()
                        .registry(Registry.STRUCTURE_REGISTRY)
                        .map(r -> r.containsKey(new ResourceLocation("questown", "empty_town")))
                        .orElse(false)
        );
    }

    private static LevelCheck emptyTownStructureSetCheck() {
        return new LevelCheck(
                "worldgen/empty_town_structure_set",
                "worldgen",
                level -> level.registryAccess()
                        .registry(Registry.STRUCTURE_SET_REGISTRY)
                        .map(r -> r.containsKey(new ResourceLocation("questown", "empty_town")))
                        .orElse(false)
        );
    }

    // --- UI checks ---

    /**
     * Roadmap #182: the work-request / stock-request screens only offer products of jobs the
     * town has unlocked. Exercises {@link ServerJobsRegistry#getAllOutputs} against the real
     * {@code Works} registry with controlled predicates to prove the gate (no town build needed,
     * since getAllOutputs reads only the registry + the level + the gather-knowledge function).
     * <p>
     * The gather-knowledge function is fed the always-known floor (wheat seeds — mirrors
     * {@code TownKnowledgeStore} baseKnowledge) so the floor's gating is actually tested, not
     * neutralised:
     * <ul>
     *   <li>predicate {@code false} ⇒ empty offer set — the gate suppresses even the
     *       always-known floor when no job is unlocked (the design decision for #182),</li>
     *   <li>predicate {@code true} ⇒ non-empty, and the always-known floor (wheat seeds)
     *       surfaces via its gather job (pre-#182 base intact),</li>
     *   <li>single-root predicate ⇒ non-empty and a strict subset of the full set
     *       (the gate filters by {@link JobID}, never invents items).</li>
     * </ul>
     */
    private static LevelCheck jobBoardKnowledgeGatingCheck() {
        return new LevelCheck(
                "ui/job_board_knowledge_gating",
                "ui",
                TestBlueprintRegistry::checkJobBoardKnowledgeGating
        );
    }

    private static final String GATING_CONTROL_ROOT = "farmer";
    private static final String ALWAYS_KNOWN_ITEM = "minecraft:wheat_seeds";

    private static boolean checkJobBoardKnowledgeGating(ServerLevel level) {
        try {
            // Mirror the always-known gather floor (TownKnowledgeStore baseKnowledge = wheat seeds)
            // so the gate is tested against an item that is in town knowledge regardless of discovery.
            ImmutableSet<MCTownItem> gatherFloor = ImmutableSet.of(
                    MCTownItem.fromMCItemStack(Items.WHEAT_SEEDS.getDefaultInstance())
            );
            WorksBehaviour.TownData td = new WorksBehaviour.TownData(level, prefix -> gatherFloor);

            ImmutableSet<Ingredient> all = ServerJobsRegistry.getAllOutputs(td, j -> true);
            ImmutableSet<Ingredient> none = ServerJobsRegistry.getAllOutputs(td, j -> false);
            ImmutableSet<Ingredient> oneRoot = ServerJobsRegistry.getAllOutputs(
                    td, j -> GATING_CONTROL_ROOT.equals(j.rootId())
            );

            Set<String> allNames = itemNames(all);
            Set<String> oneRootNames = itemNames(oneRoot);

            // none is empty even though the gather floor (wheat seeds) is "always known":
            // the job-unlock gate suppresses always-known items when no job is unlocked.
            boolean closesFully = none.isEmpty();
            boolean opensFully = !all.isEmpty();
            boolean floorSurfaces = allNames.contains(ALWAYS_KNOWN_ITEM);
            boolean filtersSelectively = !oneRoot.isEmpty()
                    && allNames.containsAll(oneRootNames)
                    && oneRootNames.size() < allNames.size();

            boolean ok = closesFully && opensFully && floorSurfaces && filtersSelectively;
            if (!ok) {
                QT.FLAG_LOGGER.error(
                        "[autotest] job_board_knowledge_gating FAIL: closesFully={} (none={}) opensFully={} (all={}) "
                                + "floorSurfaces={} filtersSelectively={} ({}={}, all={})",
                        closesFully, none.size(), opensFully, allNames.size(),
                        floorSurfaces, filtersSelectively, GATING_CONTROL_ROOT, oneRootNames.size(), allNames.size()
                );
            }
            return ok;
        } catch (RuntimeException e) {
            QT.FLAG_LOGGER.error("[autotest] job_board_knowledge_gating threw", e);
            return false;
        }
    }

    /**
     * Roadmap #182, integration half: closes the gap left by {@link #jobBoardKnowledgeGatingCheck()},
     * which proves the {@code getAllOutputs} filter against <em>synthetic</em> predicates only. Here the
     * gate is driven by the <em>real</em> {@link TownVillagerLearningHandle#isUnlocked} — the exact method
     * {@code VillagerHolder.isUnlocked} delegates to ({@code TownVillagerHandles} line ~246), which is the
     * reference both request surfaces pass ({@code TownWorkHandle.openMenuRequested},
     * {@code StockRequestClipboardItem.use}). It encodes ADR-0007's governing invariant — "what the player
     * can request == what a townie will build" — by unlocking exactly one real root via the real handle and
     * asserting the offer set is:
     * <ul>
     *   <li>identical to the controlled single-root predicate's offer set (the real handle gates to exactly
     *       the unlocked {@link JobID}, never more) — this is the new coverage,</li>
     *   <li>non-empty (the unlocked root's products surface),</li>
     *   <li>a strict subset of the full set (progression-locked tiers and other roots are excluded),</li>
     *   <li>and the predicate itself returns true for the unlocked root, false for a different root.</li>
     * </ul>
     */
    private static LevelCheck jobBoardRealUnlockGatingCheck() {
        return new LevelCheck(
                "ui/job_board_real_unlock_gating",
                "ui",
                TestBlueprintRegistry::checkJobBoardRealUnlockGating
        );
    }

    private static boolean checkJobBoardRealUnlockGating(ServerLevel level) {
        try {
            ImmutableSet<MCTownItem> gatherFloor = ImmutableSet.of(
                    MCTownItem.fromMCItemStack(Items.WHEAT_SEEDS.getDefaultInstance())
            );
            WorksBehaviour.TownData td = new WorksBehaviour.TownData(level, prefix -> gatherFloor);

            ImmutableSet<Ingredient> all = ServerJobsRegistry.getAllOutputs(td, j -> true);

            JobID root = pickProducingRoot(td);
            JobID otherRoot = anyRootOtherThan(root);
            if (root == null || otherRoot == null) {
                QT.FLAG_LOGGER.error(
                        "[autotest] job_board_real_unlock_gating: need >=2 roots with a producing one (root={}, other={})",
                        root, otherRoot
                );
                return false;
            }

            // The REAL gate: VillagerHolder.isUnlocked delegates straight to this handle's isUnlocked,
            // and registration ultimately seeds unlocks through this same unlockJob path.
            TownVillagerLearningHandle learning = new TownVillagerLearningHandle();
            learning.unlockJob(new UUID(0L, 1L), root);

            ImmutableSet<Ingredient> viaReal = ServerJobsRegistry.getAllOutputs(td, learning::isUnlocked);
            ImmutableSet<Ingredient> viaSynthetic = ServerJobsRegistry.getAllOutputs(td, j -> j.equals(root));

            Set<String> realNames = itemNames(viaReal);
            Set<String> syntheticNames = itemNames(viaSynthetic);
            Set<String> allNames = itemNames(all);

            boolean predicateGates = learning.isUnlocked(root) && !learning.isUnlocked(otherRoot);
            boolean realMatchesSynthetic = realNames.equals(syntheticNames);
            boolean opensForUnlocked = !viaReal.isEmpty();
            boolean strictSubset = allNames.containsAll(realNames) && realNames.size() < allNames.size();

            boolean ok = predicateGates && realMatchesSynthetic && opensForUnlocked && strictSubset;
            if (!ok) {
                QT.FLAG_LOGGER.error(
                        "[autotest] job_board_real_unlock_gating FAIL: predicateGates={} realMatchesSynthetic={} "
                                + "(real={}, synth={}) opensForUnlocked={} strictSubset={} (real={}, all={}) root={}/{}",
                        predicateGates, realMatchesSynthetic, realNames.size(), syntheticNames.size(),
                        opensForUnlocked, strictSubset, realNames.size(), allNames.size(),
                        root.rootId(), root.jobId()
                );
            }
            return ok;
        } catch (RuntimeException e) {
            QT.FLAG_LOGGER.error("[autotest] job_board_real_unlock_gating threw", e);
            return false;
        }
    }

    private static @Nullable JobID pickProducingRoot(WorksBehaviour.TownData td) {
        List<JobID> roots = new ArrayList<>(ServerJobsRegistry.getAllRootJobs());
        roots.sort(Comparator.comparing(j -> j.rootId() + "/" + j.jobId()));
        for (JobID root : roots) {
            if (!ServerJobsRegistry.getAllOutputs(td, j -> j.equals(root)).isEmpty()) {
                return root;
            }
        }
        return null;
    }

    private static @Nullable JobID anyRootOtherThan(@Nullable JobID root) {
        for (JobID j : ServerJobsRegistry.getAllRootJobs()) {
            if (!j.equals(root)) {
                return j;
            }
        }
        return null;
    }

    private static Set<String> itemNames(Collection<Ingredient> ingredients) {
        Set<String> names = new HashSet<>();
        for (Ingredient ingredient : ingredients) {
            for (ItemStack stack : ingredient.getItems()) {
                names.add(String.valueOf(ForgeRegistries.ITEMS.getKey(stack.getItem())));
            }
        }
        return names;
    }
}
