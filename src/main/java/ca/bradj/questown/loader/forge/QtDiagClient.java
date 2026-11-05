package ca.bradj.questown.loader.forge;

import ca.bradj.questown.core.network.AddWorkFromUIMessage;
import ca.bradj.questown.core.network.OpenFlagMenuMessage;
import ca.bradj.questown.core.network.OpenVillagerMenuMessage;
import ca.bradj.questown.core.network.QuestownNetwork;
import ca.bradj.questown.gui.VillagerTabsEmbedding;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;

/**
 * Screen-capture client hook, on only with {@code -Dquestown.diag=true}. Commands arrive as a
 * {@code [qtdiag] <cmd>} chat whisper or as lines in {@code <gameDir>/.qtdiag-cmd}:
 * {@code tab <type>}, {@code shot <name>}, {@code cursor <x> <y>}, {@code inquire <x> <y> <z> [item]},
 * {@code flagtab <type> [x y z]}, {@code vopen <type> [uuid-prefix]}, {@code click <x> <y>},
 * {@code use} and {@code close}.
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

    public static void onChat(ClientChatReceivedEvent event) {
        handle(event.getMessage().getString());
    }

    /** Handles a command, from chat ({@code [qtdiag] tab stats}) or the file ({@code tab stats}). */
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
                case "click" -> click(mc, Double.parseDouble(parts[1]), Double.parseDouble(parts[2]));
                case "scroll" -> scroll(mc, Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3]));
                case "close" -> close(mc);
                case "use" -> use(mc);
                case "inquire" -> inquire(mc, parts[1], parts[2], parts[3], parts.length > 4 ? parts[4] : null);
                case "vopen" -> vopen(mc, parts[1], parts.length > 2 ? parts[2] : null);
                case "cmd" -> chat(mc, parts);
                case "flagtab" -> flagtab(mc, parts[1],
                        parts.length > 2 ? parts[2] : "0",
                        parts.length > 3 ? parts[3] : "64",
                        parts.length > 4 ? parts[4] : "0");
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
                type,
                menu.showInventoryTab()
        ));
        System.out.println("[qtdiag] tab " + type + " requested");
    }

    /** Parks the pointer at a window-pixel position. */
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

    /** Scrolls through the game's own input handler, not straight into the screen. */
    private static void scroll(Minecraft mc, double x, double y, double delta) {
        cursor(mc, x, y);
        mc.execute(() -> {
            try {
                java.lang.reflect.Method onScroll = mc.mouseHandler.getClass()
                        .getDeclaredMethod("onScroll", long.class, double.class, double.class);
                onScroll.setAccessible(true);
                onScroll.invoke(mc.mouseHandler, mc.getWindow().getWindow(), 0.0, delta);
                String name = mc.screen == null ? "none" : mc.screen.getClass().getSimpleName();
                System.out.println("[qtdiag] scrolled " + delta + " on " + name);
            } catch (Throwable t) {
                System.out.println("[qtdiag] scroll failed: " + t);
            }
        });
    }

    /** Left-clicks the open screen at a window-pixel position. */
    private static void click(Minecraft mc, double x, double y) {
        cursor(mc, x, y);
        mc.execute(() -> {
            // A click can close the screen.
            var screen = mc.screen;
            if (screen == null) {
                System.out.println("[qtdiag] click: no screen open");
                return;
            }
            double gx = x * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
            double gy = y * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();
            screen.mouseClicked(gx, gy, 0);
            screen.mouseReleased(gx, gy, 0);
            System.out.println("[qtdiag] clicked " + screen.getClass().getSimpleName() + " at " + gx + "," + gy);
        });
    }

    private static void use(Minecraft mc) {
        KeyMapping.click(mc.options.keyUse.getKey());
        System.out.println("[qtdiag] use key pressed");
    }

    private static void close(Minecraft mc) {
        if (mc.screen == null) {
            System.out.println("[qtdiag] close: no screen open");
            return;
        }
        mc.screen.onClose();
        System.out.println("[qtdiag] screen closed");
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

    private static void flagtab(
            Minecraft mc,
            String type,
            String x,
            String y,
            String z
    ) {
        if (mc.player == null) {
            System.out.println("[qtdiag] flagtab: no local player");
            return;
        }
        QuestownNetwork.sendToServer(new OpenFlagMenuMessage(
                ca.bradj.questown.gui.FlagTabsEmbedding.FlagInfo.dumb(
                        new BlockPos(Integer.parseInt(x), Integer.parseInt(y), Integer.parseInt(z)),
                        false,
                        false
                ),
                type
        ));
        System.out.println("[qtdiag] flagtab " + type + " at " + x + "," + y + "," + z + " sent");
    }

    private static void vopen(Minecraft mc, String type, @Nullable String uuidPrefix) {
        // Open a villager's menu without the console; needs the flag's villagers menu open first.
        if (mc.player == null) {
            System.out.println("[qtdiag] vopen: no local player");
            return;
        }
        if (!(mc.player.containerMenu instanceof ca.bradj.questown.gui.MultiStatusMenu flagMenu)) {
            System.out.println("[qtdiag] vopen: open the flag villagers menu first (flagtab villagers)");
            return;
        }
        BlockPos pos = flagMenu.getFlagInfo().flagPos();
        var villagers = ca.bradj.questown.gui.town.status.MultiStatusScreen.syncedData.villagerStatuses();
        if (villagers.isEmpty()) {
            System.out.println("[qtdiag] vopen: no villager UUIDs synced to the flag menu yet");
            return;
        }
        // Pick by UUID prefix; the map has no order.
        java.util.UUID villager = villagers.keySet().stream()
                .filter(u -> uuidPrefix == null || u.toString().startsWith(uuidPrefix))
                .findFirst()
                .orElse(null);
        if (villager == null) {
            System.out.println("[qtdiag] vopen: no synced villager matches " + uuidPrefix);
            return;
        }
        QuestownNetwork.sendToServer(new OpenVillagerMenuMessage(
                pos.getX(), pos.getY(), pos.getZ(), villager, type, true
        ));
        System.out.println("[qtdiag] vopen " + type + " for villager " + villager + " at " + pos.toShortString() + " sent");
    }

    private static void chat(Minecraft mc, String[] parts) {
        // Run a command as the player, for when the server console is unavailable.
        if (mc.player == null) {
            System.out.println("[qtdiag] cmd: no local player");
            return;
        }
        String line = String.join(" ", java.util.Arrays.copyOfRange(parts, 1, parts.length));
        if (line.isEmpty()) {
            System.out.println("[qtdiag] cmd: empty command");
            return;
        }
        if (!mc.player.connection.sendUnsignedCommand(line)) {
            mc.player.connection.sendCommand(line);
        }
        System.out.println("[qtdiag] cmd sent: " + line);
    }

    private static void shot(Minecraft mc, String name) {
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
            // The screenshot is written in the background; wait for it, then move it to diags/<name>.png.
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
