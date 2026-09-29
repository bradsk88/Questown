package ca.bradj.questown.devtools;

import ca.bradj.questown.core.network.OpenVillagerMenuMessage;
import ca.bradj.questown.core.network.QuestownNetwork;
import ca.bradj.questown.gui.VillagerTabsEmbedding;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraftforge.network.PacketDistributor;

import java.io.File;
import java.util.List;

/**
 * Port-test client hook (1.19.2 golden side). Same protocol as the 1.20.6
 * QtDiagClient (tools/porttest/PORTTEST.md), file-command channel only — the
 * chat listener is not needed for the harness.
 *
 * Active only with {@code -Dquestown.diag=true}. Commands (one per line in
 * {@code <gameDir>/.qtdiag-cmd}):
 * <ul>
 *   <li>{@code tab <type>} — {@link OpenVillagerMenuMessage} from the current
 *       tabbed villager menu (same as a tab click);</li>
 *   <li>{@code shot <name>} — {@link Screenshot} to {@code <gameDir>/diags/<name>.png}.</li>
 * </ul>
 */
public class QtDiagClient {

    public static void install() {
        if (!Boolean.getBoolean("questown.diag")) {
            return;
        }
        File cmdFile = new File(Minecraft.getInstance().gameDirectory, ".qtdiag-cmd");
        Thread poller = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(250);
                    if (!java.nio.file.Files.exists(cmdFile.toPath())) {
                        continue;
                    }
                    List<String> lines = java.nio.file.Files.readAllLines(cmdFile.toPath());
                    java.nio.file.Files.delete(cmdFile.toPath());
                    for (String line : lines) {
                        if (line != null && !line.isBlank()) {
                            Minecraft.getInstance().execute(() -> handle(line.trim()));
                        }
                    }
                } catch (InterruptedException e) {
                    return;
                } catch (Throwable t) {
                    System.out.println("[qtdiag] poller tick failed: " + t);
                }
            }
        }, "qtdiag-poller");
        poller.setDaemon(true);
        poller.start();
        System.out.println("[qtdiag] client hook active (file channel, " + cmdFile + ")");
    }

    public static void handle(String text) {
        if (!Boolean.getBoolean("questown.diag") || text == null || text.isEmpty()) {
            return;
        }
        if (text.startsWith("[qtdiag] ")) {
            text = text.substring("[qtdiag] ".length());
        }
        String[] parts = text.trim().split("\\s+");
        if (parts.length < 1 || parts[0].isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        try {
            switch (parts[0]) {
                case "tab" -> tab(mc, parts[1]);
                case "shot" -> shot(mc, parts.length > 1 ? parts[1] : "shot");
                case "cursor" -> cursor(mc, Double.parseDouble(parts[1]), Double.parseDouble(parts[2]));
                case "connect" -> connect(mc, parts[1], parts.length > 2 ? Integer.parseInt(parts[2]) : 25565);
                default -> System.out.println("[qtdiag] unknown command: " + parts[0]);
            }
        } catch (Throwable t) {
            System.out.println("[qtdiag] action failed: " + t);
        }
    }

    /** Connect to a server from the title screen (quick-connect fallback when
     *  the --server game arg did not auto-connect). */
    /**
     * Park the pointer at a fixed window-pixel position. The screen's
     * mouseX/mouseY come from {@code MouseHandler.xpos()/ypos()}; those are
     * private, so set them by reflection. Dev-diag only; the pointer only
     * moves again on a real OS mouse event.
     */
    private static void cursor(Minecraft mc, double x, double y) {
        try {
            java.lang.reflect.Field fx = mc.mouseHandler.getClass().getDeclaredField("xpos");
            java.lang.reflect.Field fy = mc.mouseHandler.getClass().getDeclaredField("ypos");
            fx.setAccessible(true);
            fy.setAccessible(true);
            mc.execute(() -> {
                try {
                    fx.set(mc.mouseHandler, x);
                    fy.set(mc.mouseHandler, y);
                    System.out.println("[qtdiag] cursor parked at " + x + "," + y);
                } catch (Throwable t) {
                    System.out.println("[qtdiag] cursor failed: " + t);
                }
            });
        } catch (Throwable t) {
            System.out.println("[qtdiag] cursor failed: " + t);
        }
    }

    private static void connect(Minecraft mc, String host, int port) {
        if (mc.player != null) {
            System.out.println("[qtdiag] already in a world");
            return;
        }
        net.minecraft.client.multiplayer.ServerData sd = new net.minecraft.client.multiplayer.ServerData(
                "dev", host + ":" + port, false);
        net.minecraft.client.multiplayer.resolver.ServerAddress addr =
                new net.minecraft.client.multiplayer.resolver.ServerAddress(host, port);
        net.minecraft.client.gui.screens.ConnectScreen.startConnecting(mc.screen, mc, addr, sd);
        System.out.println("[qtdiag] connecting to " + host + ":" + port);
    }

    private static void tab(Minecraft mc, String type) {
        if (mc.player == null || !(mc.player.containerMenu instanceof VillagerTabsEmbedding menu)) {
            System.out.println("[qtdiag] tab: no tabbed villager menu open");
            return;
        }
        if (!menu.getEnabledTabs().contains(type)) {
            System.out.println("[qtdiag] tab not enabled here: " + type
                    + " (enabled: " + menu.getEnabledTabs() + ")");
            return;
        }
        OpenVillagerMenuMessage msg = new OpenVillagerMenuMessage(
                menu.getFlagPos().getX(),
                menu.getFlagPos().getY(),
                menu.getFlagPos().getZ(),
                menu.getVillagerUUID(),
                type
        );
        QuestownNetwork.CHANNEL.send(PacketDistributor.SERVER.noArg(), msg);
        System.out.println("[qtdiag] tab " + type + " requested");
    }

    private static void shot(Minecraft mc, String name) {
        // 1.19.2 Screenshot.grab(File dir, ...) writes <dir>/screenshots/<token>
        // (same dir semantics as 1.20.6 — confirmed in _grab bytecode).
        File base = new File(mc.gameDirectory, "diags");
        File outShotDir = new File(base, "screenshots");
        if (!outShotDir.exists() && !outShotDir.mkdirs()) {
            System.out.println("[qtdiag] cannot create " + outShotDir);
            return;
        }
        final String token = "qtdiag-" + System.nanoTime() + "-" + name;
        mc.execute(() -> {
            try {
                Screenshot.grab(base, token, mc.getMainRenderTarget(), c -> {
                });
            } catch (Throwable t) {
                System.out.println("[qtdiag] shot failed: " + t);
                return;
            }
            File src = new File(outShotDir, token);
            File dest = new File(base, name + ".png");
            long deadline = System.currentTimeMillis() + 8000;
            while (System.currentTimeMillis() < deadline && !src.exists()) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    return;
                }
            }
            if (!src.exists()) {
                System.out.println("[qtdiag] shot timed out: " + src.getAbsolutePath());
                return;
            }
            try {
                java.nio.file.Path dp = dest.toPath();
                java.nio.file.Path tmp = dp.resolveSibling(dest.getName() + ".tmp");
                java.nio.file.Files.move(src.toPath(), tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                java.nio.file.Files.move(tmp, dp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                System.out.println("[qtdiag] shot " + dest.getAbsolutePath());
            } catch (Throwable t) {
                System.out.println("[qtdiag] shot move failed: " + t);
            }
        });
    }
}
