package ca.bradj.questown.commands;

import ca.bradj.questown.QT;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import ca.bradj.questown.town.entity.TownFlags;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;

import java.util.UUID;

// Dev-only: the job change a player requests on a villager's change job tab by
// spending a Block of Progress (the change takes effect overnight). Thin wrapper
// over TownFlagBlockEntity.spendBopToRequestJobChange, which makes the same
// handle call the tab's network message makes; this command only resolves the
// villager and reports the result. The villager's UUID is read from the game
// surface (`_qtdev inspect villagers`). No permission gate, so it runs from the
// console (FIFO).
public class ChangeJobCommand {

    public static void register(CommandDispatcher<CommandSourceStack> src) {
        // @formatter:off
        src.register(
            Commands.literal("_qtdev").then(
                Commands.literal("change_job")
                    .then(Commands.argument("villager", UuidArgument.uuid())
                        .executes(css -> run(css.getSource(),
                                UuidArgument.getUuid(css, "villager"))))
            )
        );
        // @formatter:on
    }

    private static int run(CommandSourceStack source, UUID villagerUUID) {
        for (TownFlagBlockEntity flag : TownFlags.all()) {
            if (flag.getBlockPos() == null) {
                continue;
            }
            switch (flag.spendBopToRequestJobChange(villagerUUID)) {
                case REQUESTED -> {
                    QT.FLAG_LOGGER.info("[change_job] spent 1 Block of Progress from the flag and requested "
                            + "a job change for villager {} (takes effect overnight)", villagerUUID);
                    return 0;
                }
                case NO_BOP -> {
                    QT.FLAG_LOGGER.warn("[change_job] the flag holds no Block of Progress to spend - "
                            + "a job change request needs one", flag.getUUID());
                    return 1;
                }
                case NO_VILLAGER -> {
                    QT.FLAG_LOGGER.warn("[change_job] no villager {} in town for flag {}",
                            villagerUUID, flag.getUUID());
                    return 1;
                }
            }
        }
        QT.FLAG_LOGGER.warn("[change_job] no flag to change a job for");
        return 1;
    }
}
