package ca.bradj.questown.loader.neo;

import ca.bradj.questown.core.network.AddWorkFromUIMessage;
import ca.bradj.questown.core.network.OpenVillagerMenuMessage;
import ca.bradj.questown.core.network.QuestownNetwork;
import ca.bradj.questown.gui.VillagerTabsEmbedding;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;

import java.io.File;
import java.util.List;

/**
 * Port-test client hook. Active only when the client is launched with
 * {@code -Dquestown.diag=true} (the port-test runner does this; production
 * clients never see it). The 1.19.2 golden build carries the equivalent hook
 * (see tools/porttest/PORTTEST.md), so both versions speak the same protocol.
 *
 * Two command channels, same core:
 * <ul>
 *   <li><b>chat token</b> — a whisper from the server
 *       ({@code /msg Dev [qtdiag] <cmd>}) on the
 *       {@link ClientChatReceivedEvent} game-bus;</li>
 *   <li><b>command file</b> — one bare command per line appended to
 *       {@code <gameDir>/.qtdiag-cmd}, consumed at ~4 Hz. This needs no
 *       server console at all (the dev server's stdin may be a dead fifo).</li>
 * </ul>
 * Commands: {@code tab <type>} (open a villager tab from the current tabbed
 * menu, same {@link OpenVillagerMenuMessage} a tab click sends),
 * {@code shot <name>} (render-thread {@link Screenshot} to
 * {@code <gameDir>/diags/<name>.png}), {@code cursor <x> <y>} (park the
 * pointer at a fixed window-pixel position so hover-dependent render state —
 * e.g. the advancements-pane tooltip fade — is identical across captures), and
 * {@code inquire <x> <y> <z> [item]} (send the work-board card's
 * {@code AddWorkFromUIMessage(INQUIRED)} so the server answers with a
 * {@code WorkRequestConfirmMessage} and the confirm screen opens on the
 * render thread — the same message pair a card click produces).
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
        System.out.println("[qtdiag] client hook active (chat + " + cmdFile + ")");
    }

    /** Called from the game-bus {@link ClientChatReceivedEvent} listener. */
    public static void onChat(ClientChatReceivedEvent event) {
        handle(event.getMessage().getString());
    }

    /**
     * Core command handler. Accepts either chat-token form
     * ({@code [qtdiag] tab stats}) or bare file lines ({@code tab stats}).
     */
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
                case "inquire" -> inquire(mc, parts[1], parts[2], parts[3], parts.length > 4 ? parts[4] : null);
                default -> System.out.println("[qtdiag] unknown command: " + parts[0]);
            }
        } catch (Throwable t) {
            System.out.println("[qtdiag] action failed: " + t);
        }
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
        QuestownNetwork.sendToServer(new OpenVillagerMenuMessage(
                menu.getFlagPos().getX(),
                menu.getFlagPos().getY(),
                menu.getFlagPos().getZ(),
                menu.getVillagerUUID(),
                type
        ));
        System.out.println("[qtdiag] tab " + type + " requested");
    }

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

    private static void inquire(
            Minecraft mc,
            String x,
            String y,
            String z,
            String item
    ) {
        if (mc.player == null) {
            System.out.println("[qtdiag] inquire: no local player");
            return;
        }
        Item resolved = item == null
                ? Items.WHEAT
                : net.minecraft.core.registries.BuiltInRegistries.ITEM.get(new ResourceLocation(item));
        if (resolved == null) {
            System.out.println("[qtdiag] inquire: unknown item " + item);
            return;
        }
        QuestownNetwork.sendToServer(new AddWorkFromUIMessage(
                Ingredient.of(resolved),
                new BlockPos(Integer.parseInt(x), Integer.parseInt(y), Integer.parseInt(z)),
                AddWorkFromUIMessage.Action.INQUIRED
        ));
        System.out.println("[qtdiag] inquire " + item + " at " + x + "," + y + "," + z + " sent");
    }

    private static void shot(Minecraft mc, String name) {
        // 1.20.6 Screenshot.grab(File dir, ...) writes to <dir>/screenshots/<token>.png.
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
            // The write is async (IO worker). Poll from a daemon thread for
            // the token file, then move it to diags/<name>.png for a stable path.
            // grab(dir, token, ...) writes <dir>/screenshots/<token> (no
            // extension is added; the name is used verbatim).
            File src = new File(outShotDir, token);
            File dest = new File(base, name + ".png");
            Thread mover = new Thread(() -> {
                long deadline = System.currentTimeMillis() + 8000;
                while (System.currentTimeMillis() < deadline && !src.exists()) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
                try {
                    if (!src.exists()) {
                        System.out.println("[qtdiag] shot timed out: " + src.getAbsolutePath());
                        return;
                    }
                    java.nio.file.Path dp = dest.toPath();
                    java.nio.file.Path tmp = dp.resolveSibling(dest.getName() + ".tmp");
                    java.nio.file.Files.move(src.toPath(), tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    java.nio.file.Files.move(tmp, dp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    System.out.println("[qtdiag] shot " + dest.getAbsolutePath());
                } catch (Throwable t) {
                    System.out.println("[qtdiag] shot move failed: " + t);
                }
            }, "qtdiag-shot-" + name);
            mover.setDaemon(true);
            mover.start();
        });
    }
}
