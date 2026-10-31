package ca.bradj.questown.commands;

import ca.bradj.questown.QT;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.core.BlockPos;

// Dev-only: dump the work-seeker's per-job scores and gate-failure reasons for one
// root (plus the job-board requests), so a held run can show why a root has no
// preselected work without the debug-log toggle. Patterned on DevLootFireCommand.
// DEV-DIAG: raw_food hunt investigation, 2026-10-02 — grep 'DEV-DIAG' to enumerate; keep/remove per handoff
public class DevWorkScoreCommand {

    public static void register(CommandDispatcher<CommandSourceStack> src) {
        RequiredArgumentBuilder<CommandSourceStack, Coordinates> posArg = Commands.argument(
                "pos", BlockPosArgument.blockPos()
        );

        // @formatter:off
        src.register(
            Commands.literal("_qtdev").then(
                Commands.literal("work_score")
                    .then(posArg
                    .then(Commands.argument("root", StringArgumentType.word())
                        .executes(css -> run(
                            css.getSource(),
                            BlockPosArgument.getLoadedBlockPos(css, "pos"),
                            css.getArgument("root", String.class)
                        ))))
            )
        );
        // @formatter:on
    }

    private static int run(CommandSourceStack source, BlockPos target, String root) {
        TownFlagBlockEntity tfbe = QTCommands.getFlagOrBroadcast(source, target);
        if (tfbe == null) {
            return -1;
        }
        String dump = tfbe.getPossibleWork().scoreDump(root);
        for (String line : dump.split("\n")) {
            QT.FLAG_LOGGER.info("[_qtdev work_score] {}", line);
        }
        return 0;
    }
}
