package ca.bradj.questown.loader.forge;

import ca.bradj.questown.mc.Compat;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/** Dev-only: every few ticks, sends each line of {@code run/agent_say.txt} to all players as chat, then deletes the file. Off in released jars. */
public final class AgentMessageBridge {

    private static final Path FILE = Paths.get("agent_say.txt");
    private static final int POLL_INTERVAL_TICKS = 10;
    private static int ticks = 0;

    @SubscribeEvent
    public void onServerTick_qamb30(net.minecraftforge.event.TickEvent.ServerTickEvent event) { if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) { return; }
        if (FMLEnvironment.production) {
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
        // Wait until a player is there to read it.
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
