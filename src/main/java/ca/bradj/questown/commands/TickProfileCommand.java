package ca.bradj.questown.commands;

import ca.bradj.questown.QT;
import ca.bradj.questown.town.TickProfile;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

// Dev-only: enable the disabled-by-default TickProfile on a plain (non-autotest)
// server and dump the accumulated flag-tick and whole-server-tick stats. Lets a
// lag incident be profiled live on the real world without the perf/* autotest
// (which wipes run/world). First call enables; later calls dump the rolling
// stats since enable. Read-only measurement; no permission gate so it can be run
// from the console (FIFO) without a creative player.
public class TickProfileCommand {
    public static void register(CommandDispatcher<CommandSourceStack> src) {
        src.register(
            Commands.literal("qt").then(
                Commands.literal("tickprofile")
                    .executes(css -> run())
                    .then(Commands.literal("reset").executes(css -> reset()))
            )
        );
    }

    private static int run() {
        TickProfile tp = TickProfile.INSTANCE;
        if (!tp.isEnabled()) {
            tp.enable();
            QT.FLAG_LOGGER.info(
                "[tickprofile] enabled; measuring flag-tick + whole-server-tick from here. "
                    + "Run again after a while to dump the accumulated stats."
            );
            return 0;
        }
        QT.FLAG_LOGGER.info("[tickprofile] server tick: " + tp.snapshotServer().describe());
        QT.FLAG_LOGGER.info("[tickprofile] flag tick:   " + tp.snapshot().describe());
        for (String line : tp.describePhases()) {
            QT.FLAG_LOGGER.info("[tickprofile]" + line);
        }
        QT.FLAG_LOGGER.info("[tickprofile] --- stall attribution ---");
        for (String line : tp.describeStallAttribution()) {
            QT.FLAG_LOGGER.info("[tickprofile]" + line);
        }
        return 0;
    }

    // Clear the rolling samples so the next dump measures a fresh window. Used to tell
    // whether a stall (e.g. the 931ms server-tick max) recurs or was a one-time spike.
    private static int reset() {
        TickProfile tp = TickProfile.INSTANCE;
        if (!tp.isEnabled()) {
            QT.FLAG_LOGGER.info("[tickprofile] not enabled; nothing to reset");
            return 0;
        }
        tp.reset();
        QT.FLAG_LOGGER.info("[tickprofile] reset rolling samples; measuring a fresh window from here");
        return 0;
    }
}
