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
import java.util.UUID;

@Mod.EventBusSubscriber(modid = Questown.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class AutoTestRunner {

    private static final String SYSPROP = "questown.autotest";
    private static final String SYSPROP_WARP = "questown.autotest.warp";
    private static final String ENV_CATEGORY = "QUESTOWN_AUTOTEST_CATEGORY";
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
        String scope = category != null ? "category=" + category : "all";
        QT.FLAG_LOGGER.info("[autotest] Starting automated test suite ({}, warp={}, origin={})", scope, warpAmount, ORIGIN.toShortString());

        LogTestOutput output = new LogTestOutput("autotest");
        TestAllExecutor executor = new TestAllExecutor(overworld, output, ORIGIN, warpAmount, category);

        AutoTestTickListener listener = new AutoTestTickListener(server, executor);
        MinecraftForge.EVENT_BUS.register(listener);
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
        String val = System.getenv(ENV_CATEGORY);
        if (val == null || val.isBlank()) {
            return null;
        }
        return val;
    }

    static class AutoTestTickListener {
        private final MinecraftServer server;
        private final TestAllExecutor executor;

        AutoTestTickListener(MinecraftServer server, TestAllExecutor executor) {
            this.server = server;
            this.executor = executor;
        }

        @SubscribeEvent
        public void onTick(TickEvent.ServerTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            if (!executor.tick()) {
                return;
            }
            MinecraftForge.EVENT_BUS.unregister(this);
            reportAndShutdown();
        }

        private void reportAndShutdown() {
            int passed = executor.getPassed();
            int total = executor.getTotal();
            boolean allPassed = passed == total;

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
