package ca.bradj.questown.core;

import ca.bradj.questown.mc.Compat;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Dev-only bridge for pushing chat to the player from outside the game — e.g. a coding
 * agent guiding a play-test without console access. Every few server ticks it reads
 * {@code run/agent_say.txt} (relative to the dev run working directory), broadcasts each
 * non-blank line to all players as system chat, and deletes the file. Inert in shipped
 * jars via {@link FMLEnvironment#production}, so this never affects real players.
 */
public final class AgentMessageBridge {

    private static final Path FILE = Paths.get("agent_say.txt");
    private static final int POLL_INTERVAL_TICKS = 10;
    private static int ticks = 0;

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (FMLEnvironment.production) {
            return;
        }
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (++ticks < POLL_INTERVAL_TICKS) {
            return;
        }
        ticks = 0;
        pump();
    }

    private static void pump() {
        if (!Files.exists(FILE)) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        // Don't drain until someone can actually see it — otherwise a message queued before the
        // player joins (or while the world is still loading) is deleted and lost.
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            return;
        }
        List<String> lines = drain();
        if (lines == null) {
            return;
        }
        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            Component msg = Compat.literal("§b§l[Guide]§r " + line);
            for (ServerPlayer player : players) {
                player.sendSystemMessage(msg);
            }
        }
    }

    private static List<String> drain() {
        try {
            List<String> lines = Files.readAllLines(FILE);
            Files.delete(FILE);
            return lines;
        } catch (IOException e) {
            return null;
        }
    }
}
