package ca.bradj.questown.commands;

import ca.bradj.questown.town.TownFlagBlockEntity;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;

public class DebugCommand {
    public static void register(CommandDispatcher<CommandSourceStack> p_137808_) {
        p_137808_.register(Commands.literal("qtdebug")
                .requires((p_137812_) -> {
                    return p_137812_.hasPermission(2);
                })
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(css -> {
                            return startDebug(css.getSource(), BlockPosArgument.getLoadedBlockPos(css, "pos"));
                        })));
    }

    private static int startDebug(
            CommandSourceStack source,
            BlockPos target
    ) {
        BlockEntity e = source.getLevel().getBlockEntity(target);
        if (!(e instanceof TownFlagBlockEntity tfbe)) {
            // TODO: Better error handling?
            return -1;
        }

        tfbe.toggleDebugMode();
        return 0;
    }
}
