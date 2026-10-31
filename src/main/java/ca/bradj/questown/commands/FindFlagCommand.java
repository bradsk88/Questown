package ca.bradj.questown.commands;

import ca.bradj.questown.QT;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import ca.bradj.questown.town.entity.TownFlags;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

// Dev-only: locate a town flag's block position by reading the in-memory
// TownFlags registry. Used to find the incident flag so a player can be
// teleported to it for lag reproduction. Read-only; no permission gate so it
// can be run from the console (FIFO) without a creative player.
public class FindFlagCommand {
    public static void register(CommandDispatcher<CommandSourceStack> src) {
        src.register(
            Commands.literal("qt").then(
                Commands.literal("findflag")
                    .executes(css -> run())
            )
        );
    }

    private static int run() {
        QT.FLAG_LOGGER.info("[findflag] {} flag(s) registered", TownFlags.all().size());
        for (TownFlagBlockEntity f : TownFlags.all()) {
            QT.FLAG_LOGGER.info("[findflag] flag {} at {}", f.getUUID(), f.getBlockPos());
        }
        return 0;
    }
}
