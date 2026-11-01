package ca.bradj.questown.commands;

import ca.bradj.questown.QT;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

// Dev-only: place a welcome mat for a town flag (TownFlagBlockEntity.placeWelcomeMat).
// `_qtdev place_mat <x> <y> <z>` places at the given block; bare `_qtdev place_mat` places it a
// block east of the flag, which a hand-placed flag always has free and which is never its door.
public class FlagCraftCommand {
    public static void register(CommandDispatcher<CommandSourceStack> src) {
        RequiredArgumentBuilder<CommandSourceStack, Coordinates> posArg = Commands.argument(
                "pos",
                BlockPosArgument.blockPos()
        );

        // @formatter:off
        src.register(
            Commands.literal("_qtdev").then(
                Commands.literal("place_mat")
                    .executes(css -> runAtFlagBeside(css.getSource()))
                    .then(posArg
                        .executes(css -> run(
                            css.getSource(),
                            BlockPosArgument.getLoadedBlockPos(css, "pos")
                        )))
            )
        );
        // @formatter:on
    }

    /** The no-coordinate form: the welcome mat a block east of the flag. */
    private static int runAtFlagBeside(CommandSourceStack source) {
        TownFlagBlockEntity flag = firstFlag();
        if (flag == null) {
            QT.FLAG_LOGGER.warn("[place_mat] no flag to place the welcome mat for");
            return 1;
        }
        BlockPos target = flag.getBlockPos().east();
        flag.placeWelcomeMat(target);
        QT.FLAG_LOGGER.info("[place_mat] placed a welcome mat at {} (no coordinate given, beside the flag)",
                target);
        return 0;
    }

    private static int run(CommandSourceStack source, BlockPos pos) {
        TownFlagBlockEntity flag = firstFlag();
        if (flag == null) {
            QT.FLAG_LOGGER.warn("[place_mat] no flag to place the welcome mat for");
            return 1;
        }
        flag.placeWelcomeMat(pos);
        QT.FLAG_LOGGER.info("[place_mat] placed a welcome mat at {}", pos);
        return 0;
    }

    private static @Nullable TownFlagBlockEntity firstFlag() {
        for (TownFlagBlockEntity f : ca.bradj.questown.town.entity.TownFlags.all()) {
            if (f.getBlockPos() != null) {
                return f;
            }
        }
        return null;
    }
}
