package ca.bradj.questown.commands;

import ca.bradj.questown.QT;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

// Dev-only: claim the economics tab's free food without a client.
public class FreeFoodCommand {
    public static void register(CommandDispatcher<CommandSourceStack> src) {
        src.register(
            Commands.literal("_qtdev").then(
                Commands.literal("free_food")
                    .executes(css -> run(css.getSource()))
            )
        );
    }

    private static int run(CommandSourceStack source) {
        for (TownFlagBlockEntity flag : ca.bradj.questown.town.entity.TownFlags.all()) {
            if (flag.getBlockPos() == null) {
                continue;
            }
            if (flag.getGivenBonusFood()) {
                QT.FLAG_LOGGER.warn("[free_food] bonus food already given for flag {}", flag.getUUID());
                return 1;
            }
            int deposited = flag.depositBonusCarrots();
            if (deposited == 0) {
                QT.FLAG_LOGGER.warn("[free_food] no registered container for flag {}", flag.getUUID());
                return 1;
            }
            // Only once the food is in a chest, so a town with no chest does not use up the claim.
            flag.markBonusFoodGiven();
            QT.FLAG_LOGGER.info("[free_food] deposited {} carrots into a registered container for flag {}",
                    deposited, flag.getUUID());
            return 0;
        }
        QT.FLAG_LOGGER.warn("[free_food] no flag to give bonus food for");
        return 1;
    }
}
