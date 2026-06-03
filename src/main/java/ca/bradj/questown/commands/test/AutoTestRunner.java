package ca.bradj.questown.commands.test;

import ca.bradj.questown.QT;
import ca.bradj.questown.Questown;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import ca.bradj.questown.commands.test.TestBlueprintRegistry.AnyTestEntry;

@Mod.EventBusSubscriber(modid = Questown.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class AutoTestRunner {

    private static final String SYSPROP = "questown.autotest";
    private static final String SYSPROP_WARP = "questown.autotest.warp";
    private static final String ENV_CATEGORY = "QUESTOWN_AUTOTEST_CATEGORY";
    private static final String ENV_ONLY = "QUESTOWN_AUTOTEST_ONLY";
    private static final String SYSPROP_ONLY = "questown.autotest.only";
    private static final String CATEGORY_CHICKEN = ChickenArcBlueprintRegistry.CATEGORY;
    private static final int DEFAULT_WARP = 24000;
    private static final BlockPos ORIGIN = new BlockPos(0, 64, 0);

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        if (!"true".equals(System.getProperty(SYSPROP))) {
            return;
        }

        MinecraftServer server = event.getServer();
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            QT.FLAG_LOGGER.error("[autotest] Overworld not available, aborting");
            server.halt(false);
            return;
        }

        QT.FLAG_LOGGER.info("[autotest] BOOT_OK");

        // Force-load the test chunk so block entity tickers run without a player
        ChunkPos chunkPos = new ChunkPos(ORIGIN);
        overworld.setChunkForced(chunkPos.x, chunkPos.z, true);
        QT.FLAG_LOGGER.info("[autotest] Force-loaded chunk at {}", chunkPos);

        // Spawn a fake player near origin so the town flag doesn't go inactive
        GameProfile profile = new GameProfile(UUID.randomUUID(), "[AutoTest]");
        var fakePlayer = FakePlayerFactory.get(overworld, profile);
        fakePlayer.setPos(ORIGIN.getX(), ORIGIN.getY() + 1, ORIGIN.getZ());
        overworld.addNewPlayer(fakePlayer);
        QT.FLAG_LOGGER.info("[autotest] Spawned fake player at origin");

        int warpAmount = parseWarpAmount();
        String category = parseCategory();
        String only = parseOnly();
        String scope = describeScope(category, only);
        QT.FLAG_LOGGER.info("[autotest] Starting automated test suite ({}, warp={}, origin={})", scope, warpAmount, ORIGIN.toShortString());

        LogTestOutput output = new LogTestOutput("autotest");

        boolean runChicken = shouldRunChickenTrack(category);
        boolean runJobs = shouldRunJobsTrack(category);

        List<AnyTestEntry> jobsList = runJobs ? TestBlueprintRegistry.resolveJobs(category) : List.of();
        List<ChickenArcBlueprint> chickenList = runChicken ? ChickenArcBlueprintRegistry.all() : List.of();

        if (only != null) {
            jobsList = jobsList.stream().filter(e -> nameMatches(e.name(), only)).toList();
            chickenList = chickenList.stream().filter(b -> nameMatches(b.name(), only)).toList();
            runJobs = !jobsList.isEmpty();
            runChicken = !chickenList.isEmpty();
            if (!runJobs && !runChicken) {
                reportNoMatchAndShutdown(server, only, category);
                return;
            }
        }

        TestAllExecutor jobsExecutor = runJobs
                ? new TestAllExecutor(overworld, output, ORIGIN, warpAmount, jobsList)
                : null;
        ChickenArcAllExecutor chickenExecutor = runChicken
                ? new ChickenArcAllExecutor(overworld, server, fakePlayer, ORIGIN, output, chickenList)
                : null;

        AutoTestTickListener listener = new AutoTestTickListener(
                server, jobsExecutor, chickenExecutor
        );
        MinecraftForge.EVENT_BUS.register(listener);
    }

    private static boolean shouldRunChickenTrack(@Nullable String category) {
        if (category == null) {
            return true;
        }
        return CATEGORY_CHICKEN.equals(category);
    }

    private static boolean shouldRunJobsTrack(@Nullable String category) {
        if (category == null) {
            return true;
        }
        return !CATEGORY_CHICKEN.equals(category);
    }

    private static int parseWarpAmount() {
        String val = System.getProperty(SYSPROP_WARP);
        if (val != null) {
            try {
                return Integer.parseInt(val);
            } catch (NumberFormatException e) {
                QT.FLAG_LOGGER.warn("[autotest] Invalid warp amount '{}', using default {}", val, DEFAULT_WARP);
            }
        }
        return DEFAULT_WARP;
    }

    @Nullable
    private static String parseCategory() {
        // Prefer the env var (documented contract) but fall back to the matching
        // -D system property so the runbook's `-DQUESTOWN_AUTOTEST_CATEGORY=...`
        // invocation works without passing through a shell-env layer.
        String val = System.getenv(ENV_CATEGORY);
        if (val == null || val.isBlank()) {
            val = System.getProperty(ENV_CATEGORY);
        }
        if (val == null || val.isBlank()) {
            return null;
        }
        return val;
    }

    @Nullable
    private static String parseOnly() {
        String val = System.getenv(ENV_ONLY);
        if (val == null || val.isBlank()) {
            val = System.getProperty(SYSPROP_ONLY);
        }
        if (val == null || val.isBlank()) {
            return null;
        }
        return val;
    }

    private static boolean nameMatches(String name, String only) {
        return name.toLowerCase(Locale.ROOT).contains(only.toLowerCase(Locale.ROOT));
    }

    private static String describeScope(@Nullable String category, @Nullable String only) {
        String base = category != null ? "category=" + category : "all";
        return only != null ? base + ", only=" + only : base;
    }

    private static void reportNoMatchAndShutdown(
            MinecraftServer server,
            String only,
            @Nullable String category
    ) {
        List<String> available = new ArrayList<>();
        if (shouldRunJobsTrack(category)) {
            TestBlueprintRegistry.resolveJobs(category).forEach(e -> available.add(e.name()));
        }
        if (shouldRunChickenTrack(category)) {
            ChickenArcBlueprintRegistry.all().forEach(b -> available.add(b.name()));
        }
        QT.FLAG_LOGGER.error("[autotest] only='{}' matched 0 of {} scenarios", only, available.size());
        QT.FLAG_LOGGER.error("[autotest] available scenarios: {}", available);
        QT.FLAG_LOGGER.info("[autotest] RESULT: 0/0 passed (FAILURES)");
        server.halt(false);
        Runtime.getRuntime().halt(1);
    }

    static class AutoTestTickListener {
        private final MinecraftServer server;
        private final @Nullable TestAllExecutor jobsExecutor;
        private final @Nullable ChickenArcAllExecutor chickenExecutor;
        private boolean jobsSuiteStartAnnounced;
        private boolean jobsDone;
        private boolean chickenSuiteStartAnnounced;
        private boolean chickenDone;

        AutoTestTickListener(
                MinecraftServer server,
                @Nullable TestAllExecutor jobsExecutor,
                @Nullable ChickenArcAllExecutor chickenExecutor
        ) {
            this.server = server;
            this.jobsExecutor = jobsExecutor;
            this.chickenExecutor = chickenExecutor;
        }

        @SubscribeEvent
        public void onTick(TickEvent.ServerTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            if (!jobsDone) {
                if (jobsExecutor == null) {
                    jobsDone = true;
                } else {
                    if (!jobsSuiteStartAnnounced) {
                        QT.FLAG_LOGGER.info("[autotest] jobs:SUITE_START");
                        jobsSuiteStartAnnounced = true;
                    }
                    if (!jobsExecutor.tick()) {
                        return;
                    }
                    jobsDone = true;
                }
            }
            if (!chickenDone) {
                if (chickenExecutor == null) {
                    chickenDone = true;
                } else {
                    if (!chickenSuiteStartAnnounced) {
                        QT.FLAG_LOGGER.info("[autotest] chicken-arc:SUITE_START");
                        chickenSuiteStartAnnounced = true;
                    }
                    if (!chickenExecutor.tick()) {
                        return;
                    }
                    chickenDone = true;
                }
            }
            MinecraftForge.EVENT_BUS.unregister(this);
            reportAndShutdown();
        }

        private void reportAndShutdown() {
            int passed = 0;
            int total = 0;
            if (jobsExecutor != null) {
                passed += jobsExecutor.getPassed();
                total += jobsExecutor.getTotal();
            }
            if (chickenExecutor != null) {
                passed += chickenExecutor.getPassed();
                total += chickenExecutor.getTotal();
            }
            boolean allPassed = total > 0 && passed == total;

            QT.FLAG_LOGGER.info("[autotest] ========================================");
            QT.FLAG_LOGGER.info("[autotest] RESULT: {}/{} passed ({})",
                    passed, total, allPassed ? "ALL PASS" : "FAILURES");
            QT.FLAG_LOGGER.info("[autotest] ========================================");

            server.halt(false);
            if (!allPassed) {
                Runtime.getRuntime().halt(1);
            }
        }
    }
}
